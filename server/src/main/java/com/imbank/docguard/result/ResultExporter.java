package com.imbank.docguard.result;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imbank.docguard.studio.StudioModels;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 결과 내려받기 (F16). 화면의 결과 응답({@link ResultDtos.ResultView})만 입력으로 받으므로 원문값은 들어갈 수 없습니다(BR08).
 * 판정을 새로 내리지 않고 서버에 저장된 등급과 보고서 문단을 그대로 옮깁니다(BR01).
 */
@Component
public class ResultExporter {

    private static final DateTimeFormatter KST_MINUTES =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Seoul"));

    public enum Format {
        MD("text/markdown;charset=UTF-8"),
        JSON("application/json"),
        PDF("application/pdf");

        private final String contentType;

        Format(String contentType) {
            this.contentType = contentType;
        }

        public String contentType() {
            return contentType;
        }

        public static Optional<Format> parse(String value) {
            return switch (value == null ? "" : value.toLowerCase(Locale.ROOT)) {
                case "md" -> Optional.of(MD);
                case "json" -> Optional.of(JSON);
                case "pdf" -> Optional.of(PDF);
                default -> Optional.empty();
            };
        }
    }

    /** 보고서 머리글에 쓰는 분석 정보. 등급은 보고서에서 읽어 저장해 둔 값입니다. */
    public record Meta(String fileName, String documentType, Integer pageCount, RiskGrade riskGrade,
                       boolean gradeProvisional, Boolean reviewRequired, Instant completedAt, String workflowConfigId,
                       boolean replay) {
    }

    record JsonDocument(Document document, Verdict verdict, List<ResultDtos.Section> sections,
                        List<ResultDtos.FindingView> findings, List<ResultDtos.CitationView> citations) {
    }

    record Document(String fileName, String documentType, Integer pageCount, String completedAt,
                    String workflowConfigId, boolean replay) {
    }

    record Verdict(RiskGrade riskGrade, String riskGradeLabel, boolean provisional, Boolean reviewRequired,
                   String reviewReason, StudioModels.GradeRoute gradeRoute) {
    }

    private final ObjectMapper objectMapper;

    public ResultExporter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public byte[] render(Format format, Meta meta, ResultDtos.ResultView view) {
        return format == Format.MD
                ? markdown(meta, view).getBytes(StandardCharsets.UTF_8)
                : json(meta, view);
    }

    private byte[] json(Meta meta, ResultDtos.ResultView view) {
        JsonDocument document = new JsonDocument(
                new Document(meta.fileName(), meta.documentType(), meta.pageCount(),
                        meta.completedAt() == null ? null : meta.completedAt().toString(), meta.workflowConfigId(),
                        meta.replay()),
                new Verdict(meta.riskGrade(), gradeLabel(meta), meta.gradeProvisional(), meta.reviewRequired(),
                        view.reviewReason(), view.gradeRoute()),
                view.sections(), view.findings(), view.citations());
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(document);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("결과 파일을 만들지 못했습니다.");
        }
    }

    private static String markdown(Meta meta, ResultDtos.ResultView view) {
        StringBuilder md = new StringBuilder("# 금융문서 민감정보 점검 보고서\n\n");
        if (meta.replay()) {
            md.append("> 저장해 둔 응답을 돌려주는 재생 모드 결과이며 실제 판정이 아닙니다.\n\n");
        }
        md.append("| 항목 | 내용 |\n|---|---|\n");
        row(md, "문서", meta.fileName());
        row(md, "문서 종류", meta.documentType());
        row(md, "쪽 수", meta.pageCount() == null ? null : meta.pageCount().toString());
        row(md, "위험 등급", gradeLabel(meta));
        row(md, "판정 경로", routeLabel(view.gradeRoute()));
        row(md, "사람 검토", meta.reviewRequired() == null ? null : meta.reviewRequired() ? "필요" : "불필요");
        row(md, "점검 시각", meta.completedAt() == null ? null : KST_MINUTES.format(meta.completedAt()) + " (KST)");
        row(md, "워크플로 설정", meta.workflowConfigId());
        md.append('\n');

        if (view.reviewReason() != null && !view.reviewReason().isBlank()) {
            md.append("> 사람 검토 사유: ").append(view.reviewReason().strip().replace("\n", " ")).append("\n\n");
        }
        if (view.sections().isEmpty()) {
            md.append("보고서 항목을 읽지 못했습니다. 사람이 원본 문서를 직접 확인해 주세요.\n\n");
        }
        for (ResultDtos.Section section : view.sections()) {
            md.append("## ").append(section.title()).append("\n\n");
            // 모델이 같은 제목을 반복하면 문단이 줄바꿈으로 이어져 있으므로 Markdown 문단으로 나눕니다.
            md.append(section.body().strip().replace("\n", "\n\n")).append("\n\n");
        }
        appendCitations(md, view);
        md.append("---\n\n이 파일에는 가린 표시값과 보고서 문장만 담았습니다. 분석 결과는 자체 점검 기준에 따른 참고 정보입니다.\n");
        return md.toString();
    }

    /** 본문의 【†n】 표시가 가리키는 위치를 파일만 보고도 찾을 수 있게 표로 붙입니다. */
    private static void appendCitations(StringBuilder md, ResultDtos.ResultView view) {
        if (view.citations().isEmpty()) {
            return;
        }
        Map<Integer, String> labels = new HashMap<>();
        view.findings().forEach(f -> labels.put(f.rowIndex(), f.itemLabel()));
        md.append("## 근거 위치\n\n| 근거 | 쪽 | 항목 | 필드 |\n|---|---|---|---|\n");
        for (ResultDtos.CitationView c : view.citations()) {
            md.append("| †").append(c.index())
                    .append(" | ").append(cell(c.page() == null ? null : c.page().toString()))
                    .append(" | ").append(cell(c.rowIndex() == null ? null : labels.get(c.rowIndex())))
                    .append(" | ").append(cell(c.field()))
                    .append(" |\n");
        }
        md.append('\n');
    }

    private static void row(StringBuilder md, String name, String value) {
        md.append("| ").append(name).append(" | ").append(cell(value)).append(" |\n");
    }

    /** 표 칸을 깨뜨리는 세로줄과 줄바꿈을 바꿉니다. */
    private static String cell(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.strip().replace("|", "\\|").replaceAll("\\s*[\\r\\n]+\\s*", " ");
    }

    private static String gradeLabel(Meta meta) {
        if (meta.riskGrade() == null) {
            return null;
        }
        return (meta.gradeProvisional() ? "잠정 " : "") + meta.riskGrade().label();
    }

    private static String routeLabel(StudioModels.GradeRoute route) {
        if (route == null) {
            return null;
        }
        return switch (route) {
            case RULE -> "고위험 규칙으로 판정";
            case DECISION -> "AI가 기준표로 판정";
        };
    }
}
