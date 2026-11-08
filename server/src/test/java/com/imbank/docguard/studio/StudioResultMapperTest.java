package com.imbank.docguard.studio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.imbank.docguard.TestFixtures;
import com.imbank.docguard.result.ReportParser;
import com.imbank.docguard.result.RiskGrade;
import com.imbank.docguard.studio.StudioModels.JobSnapshot;
import com.imbank.docguard.studio.StudioModels.JobStatus;
import com.imbank.docguard.studio.StudioModels.Step;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 응답 변환기. 2026-11-06 에 실제 API 로 받은 원본 응답(set02, set10)으로 확인합니다.
 */
class StudioResultMapperTest {

    static final StudioProperties.Steps STEPS = new StudioProperties.Steps(
            "step_1_parse", "Validate - 원문 인식 점검", "step_2_classify", "Information Extract - 공통민감정보 추출",
            "Match - 공식근거 정책 대조", "Validate - 추출 결과 점검", "Instruct - 민감정보 분석보고서");

    private final ObjectMapper objectMapper = new ObjectMapper();

    static StudioProperties properties(StudioProperties.Steps steps) {
        return new StudioProperties(StudioProperties.Mode.LIVE, null, null, null, "2", "http://localhost", 3000, 600,
                null, 0, steps);
    }

    private StudioResultMapper mapper(StudioProperties.Steps steps) {
        return new StudioResultMapper(objectMapper, properties(steps));
    }

    @ParameterizedTest
    @ValueSource(strings = {"set02", "set10"})
    @DisplayName("완료 응답에서 단계, 발견 항목, 좌표, 보고서, 인용을 꺼낸다")
    void mapsCompletedResponse(String tag) throws IOException {
        JsonNode raw = objectMapper.readTree(TestFixtures.rawResponse(tag).toFile());

        JobSnapshot snapshot = mapper(STEPS).map(raw);

        assertThat(snapshot.status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(snapshot.completedSteps()).containsExactly(Step.values());
        StudioModels.Outcome outcome = snapshot.outcome();
        assertThat(outcome.pageCount()).isEqualTo(1);
        assertThat(outcome.parseCheckVerdict()).isIn("green", "yellow", "red");
        assertThat(outcome.extractCheckVerdict()).isIn("green", "yellow", "red");
        assertThat(outcome.documentType()).isEqualTo("예금");
        assertThat(outcome.documentTypeConfidence()).isNotNull();

        assertThat(outcome.findings()).isNotEmpty();
        assertThat(outcome.findings()).allSatisfy(f -> {
            assertThat(f.rawValue()).isNotBlank();
            assertThat(f.itemLabel()).isNotBlank();
            assertThat(f.infoType()).isNotBlank();
            assertThat(f.policyLinked()).isTrue();
        });
        StudioModels.ExtractedFinding first = outcome.findings().getFirst();
        assertThat(first.rowIndex()).isZero();
        assertThat(first.page()).isEqualTo(1);
        assertThat(first.wordBoxes()).isNotEmpty();
        assertThat(first.wordBoxes().getFirst()).hasSize(4)
                .allSatisfy(p -> assertThat(p.x()).isBetween(0.0, 1.0));
        assertThat(first.elementBox()).hasSize(4);

        // 보고서는 JSON 문자열을 한 번 풀어야 하고, 풀고 나면 첫 줄 형식을 읽을 수 있어야 합니다.
        assertThat(outcome.reportText()).startsWith("[금융문서 민감정보 점검보고서]").contains("【†1】");
        ReportParser.ParsedReport report = ReportParser.parse(outcome.reportText());
        assertThat(report.grade()).isEqualTo(RiskGrade.HIGH);
        assertThat(report.reviewRequired()).isFalse();

        assertThat(outcome.citations()).isNotEmpty();
        StudioModels.Citation c1 = outcome.citations().getFirst();
        assertThat(c1.index()).isEqualTo(1);
        assertThat(c1.rowIndex()).isZero();
        assertThat(c1.field()).isEqualTo("원문값");
        assertThat(outcome.citations()).allSatisfy(c ->
                assertThat(c.rowIndex()).isNotNull().isBetween(0, outcome.findings().size() - 1));
    }

    @Test
    @DisplayName("노드 이름이 바뀌어도 단계 종류(step_type)로 찾는다")
    void fallsBackToStepType() throws IOException {
        JsonNode raw = objectMapper.readTree(TestFixtures.rawResponse("set02").toFile());
        StudioProperties.Steps renamed = new StudioProperties.Steps("a", "b", "c", "d", "e", "f", "g");

        JobSnapshot snapshot = mapper(renamed).map(raw);

        assertThat(snapshot.completedSteps()).containsExactlyInAnyOrder(Step.values());
        assertThat(snapshot.outcome().findings()).isNotEmpty();
        assertThat(snapshot.outcome().parseCheckVerdict()).isNotNull();
        assertThat(snapshot.outcome().extractCheckVerdict()).isNotNull();
        assertThat(ReportParser.parse(snapshot.outcome().reportText()).grade()).isEqualTo(RiskGrade.HIGH);
    }

    @Test
    @DisplayName("진행 중 응답은 끝난 단계만 돌려주고 결과는 없다")
    void mapsInProgress() throws IOException {
        ObjectNode raw = (ObjectNode) objectMapper.readTree(TestFixtures.rawResponse("set02").toFile());
        raw.put("status", "in_progress");
        ArrayNode output = (ArrayNode) raw.get("output");
        while (output.size() > 3) {
            output.remove(output.size() - 1);
        }

        JobSnapshot snapshot = mapper(STEPS).map(raw);

        assertThat(snapshot.status()).isEqualTo(JobStatus.IN_PROGRESS);
        assertThat(snapshot.completedSteps()).containsExactly(Step.PARSE, Step.PARSE_CHECK, Step.CLASSIFY);
        assertThat(snapshot.outcome()).isNull();
    }

    @Test
    @DisplayName("보고서 단계가 없으면 보고서 문장이 비어 있고, 파서가 UNKNOWN 으로 처리할 수 있다")
    void missingReport() {
        ObjectNode raw = objectMapper.createObjectNode();
        raw.put("status", "completed");
        raw.putArray("output");

        JobSnapshot snapshot = mapper(STEPS).map(raw);

        assertThat(snapshot.outcome().reportText()).isNull();
        assertThat(snapshot.outcome().findings()).isEmpty();
        assertThat(ReportParser.parse(snapshot.outcome().reportText()).grade()).isEqualTo(RiskGrade.UNKNOWN);
    }
}
