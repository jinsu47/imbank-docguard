package com.imbank.docguard.result;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imbank.docguard.studio.StudioModels;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResultExporterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ResultExporter exporter = new ResultExporter(objectMapper);

    private static ResultExporter.Meta meta(RiskGrade grade, boolean provisional, boolean replay) {
        return new ResultExporter.Meta("신청서|최종.pdf", "여신 신청서", 2, grade, provisional, true,
                Instant.parse("2026-11-09T12:30:00Z"), "3", replay);
    }

    private static ResultDtos.ResultView view(List<ResultDtos.Section> sections) {
        ResultDtos.FindingView finding = new ResultDtos.FindingView(0, "계좌번호", "계좌번호", "신청인", "미마스킹", null, true,
                "****-2846", null, 1, null, null, "high");
        return new ResultDtos.ResultView(sections, "검토\n사유", List.of(finding),
                List.of(new ResultDtos.CitationView(1, 0, "원문값", 1, null, null)), StudioModels.GradeRoute.DECISION);
    }

    private String md(ResultExporter.Meta meta, ResultDtos.ResultView view) {
        return new String(exporter.render(ResultExporter.Format.MD, meta, view), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("F16: Markdown 은 머리글, 항목별 문단, 근거 위치를 담고 표 칸을 깨뜨리는 문자를 바꾼다")
    void markdown() {
        String md = md(meta(RiskGrade.HIGH, false, false),
                view(List.of(new ResultDtos.Section("발견된 문제", "첫 문단【†1】\n둘째 문단"))));

        assertThat(md).contains("| 문서 | 신청서\\|최종.pdf |", "| 위험 등급 | 상 |", "| 판정 경로 | AI가 기준표로 판정 |",
                "| 사람 검토 | 필요 |", "| 점검 시각 | 2026-11-09 21:30 (KST) |", "| 워크플로 설정 | 3 |");
        assertThat(md).contains("> 사람 검토 사유: 검토 사유");
        assertThat(md).contains("## 발견된 문제\n\n첫 문단【†1】\n\n둘째 문단\n");
        assertThat(md).contains("| †1 | 1 | 계좌번호 | 원문값 |");
        assertThat(md).doesNotContain("재생 모드");
    }

    @Test
    @DisplayName("F16: 재생 모드와 잠정 등급을 표시하고, 항목을 읽지 못한 보고서는 등급을 추측하지 않는다(BR02)")
    void replayAndUnknown() {
        String md = md(meta(RiskGrade.UNKNOWN, false, true), view(List.of()));
        assertThat(md).contains("재생 모드 결과", "| 위험 등급 | 판정 확인 필요 |", "보고서 항목을 읽지 못했습니다");

        assertThat(md(meta(RiskGrade.MEDIUM, true, false), view(List.of()))).contains("| 위험 등급 | 잠정 중 |");
    }

    @Test
    @DisplayName("F16: JSON 은 문서 정보, 판정, 문단, 표시값을 담는다")
    void json() throws Exception {
        JsonNode root = objectMapper.readTree(exporter.render(ResultExporter.Format.JSON, meta(RiskGrade.HIGH, false, false),
                view(List.of(new ResultDtos.Section("요약", "본문")))));

        assertThat(root.at("/document/fileName").asText()).isEqualTo("신청서|최종.pdf");
        assertThat(root.at("/document/workflowConfigId").asText()).isEqualTo("3");
        assertThat(root.at("/verdict/riskGrade").asText()).isEqualTo("HIGH");
        assertThat(root.at("/verdict/riskGradeLabel").asText()).isEqualTo("상");
        assertThat(root.at("/verdict/gradeRoute").asText()).isEqualTo("DECISION");
        assertThat(root.at("/sections/0/title").asText()).isEqualTo("요약");
        assertThat(root.at("/findings/0/displayValue").asText()).isEqualTo("****-2846");
        assertThat(root.at("/findings/0").has("rawValue")).isFalse();
    }

    @Test
    @DisplayName("F16: 형식 이름은 md, json, pdf 만 받는다")
    void parseFormat() {
        assertThat(ResultExporter.Format.parse("MD")).contains(ResultExporter.Format.MD);
        assertThat(ResultExporter.Format.parse("json")).contains(ResultExporter.Format.JSON);
        assertThat(ResultExporter.Format.parse("pdf")).contains(ResultExporter.Format.PDF);
        assertThat(ResultExporter.Format.parse("docx")).isEmpty();
        assertThat(ResultExporter.Format.parse(null)).isEmpty();
    }
}
