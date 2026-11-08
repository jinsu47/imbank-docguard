package com.imbank.docguard.studio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 로컬 개발 전용: Upstage 를 호출하지 않고, 2026-11-06 에 실제 API 로 받아 저장한 응답({@code *_raw.json})을 돌려줍니다.
 * <ul>
 *   <li>올린 파일 이름에 {@code set02}처럼 저장 응답의 태그가 들어 있으면 그 응답을, 아니면 첫 번째 응답을 고릅니다.
 *       즉 결과는 올린 문서의 실제 판정이 아닙니다. 화면은 이 모드의 분석에 "재생 모드" 표시를 붙입니다.</li>
 *   <li>진행 표시를 시험할 수 있도록 대기 → 단계별 진행 → 완료를 몇 초에 걸쳐 흉내 냅니다.</li>
 * </ul>
 * 작업 ID 에 시작 시각을 넣어, 서버가 다시 시작되어도 같은 결과를 돌려줍니다.
 */
class ReplayStudioGateway implements StudioGateway {

    private static final Logger log = LoggerFactory.getLogger(ReplayStudioGateway.class);
    private static final Pattern TAG = Pattern.compile("set\\d{2}", Pattern.CASE_INSENSITIVE);
    private static final Pattern JOB_ID = Pattern.compile("replay-job-([a-z0-9]+)-(\\d+)");

    private final long queuedMillis;
    private final long stepMillis;

    private final Path dir;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    /** 태그(set02 등) → 파일. 순서를 지킵니다. */
    private final Map<String, Path> responses = new LinkedHashMap<>();

    ReplayStudioGateway(String replayDir, long stepMillis, ObjectMapper objectMapper, Clock clock) {
        this.stepMillis = Math.max(0, stepMillis);
        this.queuedMillis = this.stepMillis * 3 / 2;
        this.dir = Path.of(replayDir == null ? "" : replayDir).toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
        this.clock = clock;
        if (Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.list(dir)) {
                files.filter(p -> p.getFileName().toString().endsWith("_raw.json"))
                        .sorted()
                        .forEach(p -> responses.put(tagOf(p.getFileName().toString(), responses.size()), p));
            } catch (IOException e) {
                log.warn("재생 응답 폴더를 읽지 못했습니다: {}", e.getClass().getSimpleName());
            }
        }
        log.info("Studio 재생 모드: 저장 응답 {}건 ({})", responses.size(), String.join(", ", responses.keySet()));
    }

    private static String tagOf(String filename, int index) {
        Matcher m = TAG.matcher(filename);
        return m.find() ? m.group().toLowerCase(Locale.ROOT) : "r" + index;
    }

    @Override
    public String uploadFile(byte[] content, String contentType, String nameHint) {
        if (responses.isEmpty()) {
            throw new StudioException("STUDIO_REPLAY_EMPTY", false, "재생할 저장 응답(*_raw.json)이 없습니다: UPSTAGE_REPLAY_DIR 확인");
        }
        String tag = responses.keySet().iterator().next();
        if (nameHint != null) {
            Matcher m = TAG.matcher(nameHint);
            if (m.find() && responses.containsKey(m.group().toLowerCase(Locale.ROOT))) {
                tag = m.group().toLowerCase(Locale.ROOT);
            }
        }
        return "replay-file-" + tag;
    }

    @Override
    public String createJob(String fileId) {
        String tag = fileId.substring("replay-file-".length());
        return "replay-job-" + tag + "-" + clock.millis();
    }

    @Override
    public JsonNode getJob(String jobId) {
        Matcher m = JOB_ID.matcher(jobId);
        if (!m.matches() || !responses.containsKey(m.group(1))) {
            throw new StudioException("STUDIO_HTTP_404", false, "재생 작업을 찾을 수 없습니다.");
        }
        JsonNode full;
        try {
            full = objectMapper.readTree(responses.get(m.group(1)).toFile());
        } catch (IOException e) {
            throw new StudioException("STUDIO_BAD_RESPONSE", false, "저장 응답을 읽지 못했습니다.");
        }
        long elapsed = clock.millis() - Long.parseLong(m.group(2));
        ObjectNode copy = full.deepCopy();
        copy.put("id", jobId);
        ArrayNode output = (ArrayNode) copy.path("output");
        int total = output.size();
        if (elapsed < queuedMillis) {
            copy.put("status", "queued");
            output.removeAll();
        } else {
            int done = stepMillis == 0 ? total : (int) ((elapsed - queuedMillis) / stepMillis);
            if (done < total) {
                copy.put("status", "in_progress");
                while (output.size() > done) {
                    output.remove(output.size() - 1);
                }
            }
        }
        return copy;
    }

    @Override
    public void deleteFile(String fileId) {
        // 재생 모드는 Studio 에 올린 파일이 없습니다.
    }
}
