package com.imbank.docguard.studio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imbank.docguard.common.Point;
import com.imbank.docguard.studio.StudioModels.Citation;
import com.imbank.docguard.studio.StudioModels.ExtractedFinding;
import com.imbank.docguard.studio.StudioModels.JobSnapshot;
import com.imbank.docguard.studio.StudioModels.JobStatus;
import com.imbank.docguard.studio.StudioModels.Outcome;
import com.imbank.docguard.studio.StudioModels.Step;
import com.imbank.docguard.studio.StudioModels.StepRun;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Studio 작업 응답을 서비스 객체로 바꿉니다 (ARCHITECTURE.md 4장 "응답에서 쓰는 값").
 * <ul>
 *   <li>결과는 {@code output[]}에 단계별로 있고 {@code output[].model}이 단계 이름입니다.</li>
 *   <li>{@code content[0].text}와 {@code content[0].additional_values}는 문자열로 된 JSON 이라 한 번 더 파싱합니다.</li>
 *   <li>단계는 설정의 이름으로 먼저 찾고, 없으면 {@code step_type}으로 찾습니다.</li>
 *   <li>Extract 와 Match 가 모두 발견내역을 돌려주므로, 값과 좌표는 Extract 단계에서 읽습니다.</li>
 * </ul>
 * 판정(등급, 사람 검토)은 여기서 내리지 않습니다. 보고서 문장은 그대로 넘기고 result 패키지의 ReportParser 가 읽습니다.
 */
@Component
public class StudioResultMapper {

    static final String FINDINGS_KEY = "민감정보 발견내역";
    private static final Pattern SOURCE_REF = Pattern.compile("^" + FINDINGS_KEY + "\\[(\\d+)]\\.(.+)$");
    private static final String POLICY_ID_SUFFIX = "__정책번호";

    private final ObjectMapper objectMapper;
    private final StudioProperties.Steps names;

    public StudioResultMapper(ObjectMapper objectMapper, StudioProperties properties) {
        this.objectMapper = objectMapper;
        this.names = properties.steps();
    }

    public JobSnapshot map(JsonNode response) {
        JobStatus status = statusOf(response.path("status").asText(""));
        Map<Step, JsonNode> steps = locateSteps(response.path("output"));
        List<Step> completed = new ArrayList<>();
        steps.forEach((step, node) -> {
            if ("completed".equals(node.path("status").asText("completed"))) {
                completed.add(step);
            }
        });
        Outcome outcome = status == JobStatus.COMPLETED ? outcome(steps) : null;
        return new JobSnapshot(status, completed, outcome);
    }

    static JobStatus statusOf(String status) {
        return switch (status) {
            case "queued" -> JobStatus.QUEUED;
            case "in_progress" -> JobStatus.IN_PROGRESS;
            case "completed" -> JobStatus.COMPLETED;
            case "failed", "cancelled", "incomplete" -> JobStatus.FAILED;
            default -> throw new StudioException("STUDIO_BAD_RESPONSE", false, "알 수 없는 작업 상태입니다.");
        };
    }

    private Outcome outcome(Map<Step, JsonNode> steps) {
        JsonNode parseText = jsonOf(text(steps.get(Step.PARSE)));
        JsonNode classify = additional(steps.get(Step.CLASSIFY));
        JsonNode report = steps.get(Step.REPORT);

        Integer pageCount = parseText.path("usage").path("pages").isInt() ? parseText.path("usage").path("pages").asInt() : null;
        String documentType = textOrNull(classify.path("document_type").path("_value"));
        BigDecimal confidence = classify.path("document_type").path("confidence_score").isNumber()
                ? classify.path("document_type").path("confidence_score").decimalValue() : null;

        List<StepRun> runs = new ArrayList<>();
        steps.forEach((step, node) -> runs.add(new StepRun(step, node.path("model").asText(),
                additional(node).path("cache_hit").asBoolean(false))));

        return new Outcome(
                pageCount,
                verdict(steps.get(Step.PARSE_CHECK)),
                verdict(steps.get(Step.EXTRACT_CHECK)),
                documentType,
                confidence,
                findings(steps.get(Step.EXTRACT), steps.get(Step.MATCH)),
                reportText(report),
                citations(report),
                runs);
    }

    // ---------------------------------------------------------------- 단계 찾기

    private Map<Step, JsonNode> locateSteps(JsonNode output) {
        Map<Step, JsonNode> found = new EnumMap<>(Step.class);
        Map<Step, String> configured = new EnumMap<>(Step.class);
        if (names != null) {
            configured.put(Step.PARSE, names.parse());
            configured.put(Step.PARSE_CHECK, names.parseCheck());
            configured.put(Step.CLASSIFY, names.classify());
            configured.put(Step.EXTRACT, names.extract());
            configured.put(Step.MATCH, names.match());
            configured.put(Step.EXTRACT_CHECK, names.extractCheck());
            configured.put(Step.REPORT, names.report());
        }
        List<JsonNode> unmatched = new ArrayList<>();
        for (JsonNode item : output) {
            String model = item.path("model").asText("");
            Step step = configured.entrySet().stream()
                    .filter(e -> model.equals(e.getValue()))
                    .map(Map.Entry::getKey)
                    .findFirst().orElse(null);
            if (step != null && !found.containsKey(step)) {
                found.put(step, item);
            } else {
                unmatched.add(item);
            }
        }
        // 이름이 바뀌었으면 단계 종류(step_type)로 찾습니다. validate 는 Extract 앞이면 원문 인식 점검, 뒤면 추출 결과 점검입니다.
        boolean afterExtract = found.containsKey(Step.EXTRACT);
        for (JsonNode item : output) {
            if (!unmatched.contains(item)) {
                if (item == found.get(Step.EXTRACT)) {
                    afterExtract = true;
                }
                continue;
            }
            Step step = switch (item.path("step_type").asText("")) {
                case "document-parse" -> Step.PARSE;
                case "document-classify" -> Step.CLASSIFY;
                case "information-extract" -> Step.EXTRACT;
                case "match" -> Step.MATCH;
                case "instruct" -> Step.REPORT;
                case "validate" -> afterExtract ? Step.EXTRACT_CHECK : Step.PARSE_CHECK;
                default -> null;
            };
            if (step == Step.EXTRACT) {
                afterExtract = true;
            }
            if (step != null && !found.containsKey(step)) {
                found.put(step, item);
            }
        }
        return found;
    }

    // ---------------------------------------------------------------- 발견 항목

    private List<ExtractedFinding> findings(JsonNode extractStep, JsonNode matchStep) {
        if (extractStep == null) {
            return List.of();
        }
        JsonNode rows = additional(extractStep).path(FINDINGS_KEY);
        JsonNode plainRows = jsonOf(text(extractStep)).path(FINDINGS_KEY);
        if (!rows.isArray() || rows.isEmpty()) {
            rows = plainRows;
        }
        JsonNode matchRows = matchStep == null ? null : jsonOf(text(matchStep)).path(FINDINGS_KEY);

        List<ExtractedFinding> result = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            JsonNode row = rows.get(i);
            JsonNode raw = row.path("원문값");
            result.add(new ExtractedFinding(
                    i,
                    value(row, "원문값"),
                    value(row, "항목명"),
                    value(row, "정보 유형"),
                    value(row, "정보주체 역할"),
                    value(row, "정보주체 식별근거"),
                    value(row, "원문 근거"),
                    value(row, "가림 상태"),
                    value(row, "값 성격"),
                    value(row, "검토 사유"),
                    policyLinked(matchRows, i),
                    pageOf(raw),
                    boxes(raw.path("word_coordinates")),
                    box(raw.path("coordinates")),
                    textOrNull(raw.path("confidence"))));
        }
        return result;
    }

    /** 필드는 {_value, page, coordinates, ...} 객체이거나(additional_values) 그냥 문자열입니다(text). */
    private static String value(JsonNode row, String field) {
        JsonNode node = row.path(field);
        if (node.isObject()) {
            node = node.path("_value");
        }
        return node.isMissingNode() || node.isNull() ? "" : node.asText();
    }

    private static boolean policyLinked(JsonNode matchRows, int index) {
        if (matchRows == null || !matchRows.isArray() || index >= matchRows.size()) {
            return false;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = matchRows.get(index).fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> e = fields.next();
            if (e.getKey().endsWith(POLICY_ID_SUFFIX) && !e.getValue().asText("").isBlank()) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 보고서와 인용

    private String reportText(JsonNode reportStep) {
        String text = text(reportStep);
        if (text == null) {
            return null;
        }
        String trimmed = text.strip();
        // 보고서는 JSON 문자열로 한 번 더 감싸여 옵니다.
        if (trimmed.startsWith("\"")) {
            try {
                return objectMapper.readValue(trimmed, String.class);
            } catch (Exception e) {
                return text;
            }
        }
        return text;
    }

    private List<Citation> citations(JsonNode reportStep) {
        JsonNode list = additional(reportStep).path("citations");
        List<Citation> result = new ArrayList<>();
        for (JsonNode c : list) {
            String ref = c.path("source_ref").asText("");
            Integer rowIndex = null;
            String field = null;
            Matcher m = SOURCE_REF.matcher(ref);
            if (m.matches()) {
                rowIndex = Integer.parseInt(m.group(1));
                field = m.group(2);
            }
            result.add(new Citation(c.path("index").asInt(result.size() + 1), ref, rowIndex, field, pageOf(c),
                    boxes(c.path("word_coordinates")), box(c.path("coordinates"))));
        }
        return result;
    }

    // ---------------------------------------------------------------- 공통

    private String verdict(JsonNode step) {
        if (step == null) {
            return null;
        }
        String fromAdditional = textOrNull(additional(step).path("verdict"));
        String fromText = text(step);
        String verdict = fromAdditional != null ? fromAdditional
                : fromText == null ? "" : fromText.replace("\"", "").strip();
        return switch (verdict) {
            case "green", "yellow", "red" -> verdict;
            default -> null;
        };
    }

    private static String text(JsonNode step) {
        if (step == null) {
            return null;
        }
        JsonNode text = step.path("content").path(0).path("text");
        return text.isTextual() ? text.asText() : null;
    }

    private JsonNode additional(JsonNode step) {
        if (step == null) {
            return objectMapper.createObjectNode();
        }
        JsonNode raw = step.path("content").path(0).path("additional_values");
        if (raw.isObject()) {
            return raw;
        }
        return jsonOf(raw.isTextual() ? raw.asText() : null);
    }

    private JsonNode jsonOf(String text) {
        if (text == null || text.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(text);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private static Integer pageOf(JsonNode node) {
        JsonNode page = node.path("page");
        return page.isInt() && page.asInt() > 0 ? page.asInt() : null;
    }

    private static List<Point> box(JsonNode node) {
        if (!node.isArray() || node.isEmpty()) {
            return null;
        }
        List<Point> points = new ArrayList<>();
        for (JsonNode p : node) {
            if (p.path("x").isNumber() && p.path("y").isNumber()) {
                points.add(new Point(p.path("x").asDouble(), p.path("y").asDouble()));
            }
        }
        return points.isEmpty() ? null : points;
    }

    private static List<List<Point>> boxes(JsonNode node) {
        if (!node.isArray() || node.isEmpty()) {
            return null;
        }
        List<List<Point>> result = new ArrayList<>();
        for (JsonNode b : node) {
            List<Point> points = box(b);
            if (points != null) {
                result.add(points);
            }
        }
        return result.isEmpty() ? null : result;
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
    }
}
