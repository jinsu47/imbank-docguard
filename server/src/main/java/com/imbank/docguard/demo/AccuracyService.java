package com.imbank.docguard.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imbank.docguard.analysis.Analysis;
import com.imbank.docguard.analysis.AnalysisRepository;
import com.imbank.docguard.analysis.AnalysisStatus;
import com.imbank.docguard.auth.AuthUser;
import com.imbank.docguard.result.RiskGrade;
import com.imbank.docguard.storage.EncryptionService;
import com.imbank.docguard.studio.StudioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 시연 정확도 (F12, US08). 내가 웹에서 실행한 평가 문서의 등급·사람 검토를 정답표(eval/answer_key.json)와 비교합니다.
 * <ul>
 *   <li>판정을 새로 내리지 않습니다. 저장된 Studio 판정을 정답표와 나란히 놓을 뿐입니다(BR01).</li>
 *   <li>문서는 파일 이름에 들어 있는 태그(set01 등)로 찾고, 태그마다 가장 최근에 완료된 분석을 씁니다.</li>
 *   <li>재생 모드 분석은 실제 판정이 아니므로 뺍니다.</li>
 *   <li>정답표는 대회 규정 확인 전까지 비공개라 저장소 밖 경로(APP_ANSWER_KEY)에서 읽고, 없으면 화면에 그렇게 알립니다.</li>
 * </ul>
 */
@Service
public class AccuracyService {

    private static final Logger log = LoggerFactory.getLogger(AccuracyService.class);

    private final AnalysisRepository analyses;
    private final EncryptionService encryption;
    private final ObjectMapper objectMapper;
    private final Path answerKey;

    public AccuracyService(AnalysisRepository analyses, EncryptionService encryption, ObjectMapper objectMapper,
                           @Value("${app.answer-key:../eval/answer_key.json}") String answerKey) {
        this.analyses = analyses;
        this.encryption = encryption;
        this.objectMapper = objectMapper;
        this.answerKey = Path.of(answerKey).toAbsolutePath().normalize();
    }

    /**
     * @param expectedGrade  정답 등급(보고서 표기: 상·중·하·노출 없음, 잠정이면 앞에 "잠정")
     * @param actualGrade    웹 실행 등급(같은 표기). 실행한 분석이 없으면 null
     * @param match          등급과 사람 검토가 모두 같으면 true, 실행 전이면 null
     */
    public record Row(String tag, String title, String expectedGrade, String expectedReview, String analysisId,
                      String actualGrade, String actualReview, Boolean gradeMatch, Boolean reviewMatch, Boolean match,
                      Instant completedAt, Long elapsedSeconds) {
    }

    public record Report(boolean available, String message, int total, int executed, int matched, List<Row> rows) {
    }

    @Transactional(readOnly = true)
    public Report report(AuthUser user) {
        if (!Files.isRegularFile(answerKey)) {
            return new Report(false, "정답표 파일이 없어 비교할 수 없습니다(APP_ANSWER_KEY 확인).", 0, 0, 0, List.of());
        }
        JsonNode docs;
        try {
            docs = objectMapper.readTree(answerKey.toFile()).path("docs");
        } catch (IOException e) {
            log.warn("정답표를 읽지 못했습니다: {}", e.getClass().getSimpleName());
            return new Report(false, "정답표를 읽지 못했습니다.", 0, 0, 0, List.of());
        }

        // 내 완료 분석을 파일 이름의 태그로 묶습니다(최근 것 우선).
        List<Analysis> mine = analyses.findTop100ByOwnerIdOrderByCreatedAtDesc(user.id()).stream()
                .filter(a -> a.getStatus() == AnalysisStatus.COMPLETED || a.getStatus() == AnalysisStatus.PURGED)
                .filter(a -> !StudioService.REPLAY_AGENT_ID.equals(a.getStudioAgentId()))
                .sorted(Comparator.comparing(Analysis::getCreatedAt).reversed())
                .toList();
        List<Map.Entry<String, Analysis>> named = new ArrayList<>();
        for (Analysis a : mine) {
            try {
                named.add(Map.entry(encryption.decryptText(a.getOriginalFilenameEnc()).toLowerCase(Locale.ROOT), a));
            } catch (RuntimeException e) {
                // 이름을 읽지 못한 분석은 비교에서 뺍니다.
            }
        }

        List<Row> rows = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> it = docs.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> doc = it.next();
            String tag = doc.getKey();
            JsonNode key = doc.getValue().has("same_as") ? docs.path(doc.getValue().path("same_as").asText()) : doc.getValue();
            String expectedGrade = key.path("grade").asText();
            String expectedReview = key.path("review").asText();
            Analysis found = named.stream()
                    .filter(e -> containsTag(e.getKey(), tag.toLowerCase(Locale.ROOT)))
                    .map(Map.Entry::getValue).findFirst().orElse(null);
            if (found == null) {
                rows.add(new Row(tag, doc.getValue().path("title").asText(), expectedGrade, expectedReview, null, null,
                        null, null, null, null, null, null));
                continue;
            }
            String actualGrade = gradeLabel(found);
            String actualReview = Boolean.TRUE.equals(found.getReviewRequired()) ? "필요" : "불필요";
            boolean gradeMatch = expectedGrade.equals(actualGrade);
            boolean reviewMatch = expectedReview.equals(actualReview);
            Long elapsed = found.getSubmittedAt() != null && found.getCompletedAt() != null
                    ? Duration.between(found.getSubmittedAt(), found.getCompletedAt()).getSeconds() : null;
            rows.add(new Row(tag, doc.getValue().path("title").asText(), expectedGrade, expectedReview,
                    found.getId().toString(), actualGrade, actualReview, gradeMatch, reviewMatch,
                    gradeMatch && reviewMatch, found.getCompletedAt(), elapsed));
        }
        int executed = (int) rows.stream().filter(r -> r.analysisId() != null).count();
        int matched = (int) rows.stream().filter(r -> Boolean.TRUE.equals(r.match())).count();
        return new Report(true, null, rows.size(), executed, matched, rows);
    }

    /** set1 이 set10 에 걸리지 않도록 태그 뒤가 숫자가 아니어야 합니다. */
    static boolean containsTag(String name, String tag) {
        int i = name.indexOf(tag);
        while (i >= 0) {
            int end = i + tag.length();
            if (end >= name.length() || !Character.isDigit(name.charAt(end))) {
                return true;
            }
            i = name.indexOf(tag, i + 1);
        }
        return false;
    }

    private static String gradeLabel(Analysis a) {
        RiskGrade grade = a.getRiskGrade();
        if (grade == null) {
            return "-";
        }
        return (a.isGradeProvisional() ? "잠정" : "") + grade.label();
    }
}
