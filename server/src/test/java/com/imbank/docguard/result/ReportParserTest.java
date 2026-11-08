package com.imbank.docguard.result;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imbank.docguard.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 보고서 파서 (ARCHITECTURE.md 4장 "보고서 해석 규칙", BR01, BR02).
 * v2.5 보고서 10건에서 읽은 등급·사람 검토가 정답표(eval/answer_key.json)와 같아야 합니다.
 */
class ReportParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("v2.5 보고서 10건의 등급과 사람 검토가 정답표와 모두 같다")
    void v25ReportsMatchAnswerKey() throws IOException {
        TestFixtures.requireEval(TestFixtures.V25_REPORTS);
        JsonNode docs = objectMapper.readTree(TestFixtures.requireEval(TestFixtures.ANSWER_KEY).toFile()).path("docs");

        List<String> checked = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> it = docs.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> doc = it.next();
            String tag = doc.getKey();
            JsonNode key = doc.getValue().has("same_as") ? docs.path(doc.getValue().path("same_as").asText()) : doc.getValue();
            Path report = findReport(tag);
            String content = objectMapper.readTree(report.toFile()).path("content").asText();

            ReportParser.ParsedReport parsed = ReportParser.parse(content);

            assertThat(parsed.parsed()).as(tag).isTrue();
            String expectedGrade = key.path("grade").asText();
            assertThat((parsed.provisional() ? "잠정" : "") + parsed.grade().label()).as(tag + " 등급").isEqualTo(expectedGrade);
            assertThat(parsed.reviewRequired() ? "필요" : "불필요").as(tag + " 사람 검토").isEqualTo(key.path("review").asText());
            assertThat(parsed.sections()).as(tag + " 항목").containsKeys("요약", "판정 근거");
            checked.add(tag);
        }
        assertThat(checked).hasSize(10);
    }

    private static Path findReport(String tag) throws IOException {
        try (Stream<Path> files = Files.list(TestFixtures.V25_REPORTS)) {
            return files.filter(p -> p.getFileName().toString().contains(tag + "_") && p.getFileName().toString().endsWith(".json"))
                    .findFirst().orElseThrow();
        }
    }

    @Test
    @DisplayName("사람 검토가 필요하면 사유 문장을 함께 읽고, 인용 표시는 판정에 영향을 주지 않는다")
    void readsReviewReason() {
        String report = """
                [금융문서 민감정보 점검보고서]

                위험 등급: 중

                사람 검토: 필요. 계좌 명의자가 확인되지 않는다.【†3】

                요약: 계좌번호가 가림 없이 포함되어 있다.【†1】

                판정 근거: 「계좌·카드번호 또는 연락처 노출」이 성립한다.【†2】
                """;
        ReportParser.ParsedReport parsed = ReportParser.parse(report);
        assertThat(parsed.grade()).isEqualTo(RiskGrade.MEDIUM);
        assertThat(parsed.reviewRequired()).isTrue();
        assertThat(parsed.reviewReason()).isEqualTo("계좌 명의자가 확인되지 않는다.【†3】");
        assertThat(parsed.sections().get("요약")).isEqualTo("계좌번호가 가림 없이 포함되어 있다.【†1】");
        assertThat(parsed.sections().get("판정 근거")).contains("【†2】");
    }

    @Test
    @DisplayName("잠정 등급을 읽는다")
    void readsProvisional() {
        ReportParser.ParsedReport parsed = ReportParser.parse("위험 등급: 잠정 상\n\n사람 검토: 필요. 판독 불가 항목이 있다.");
        assertThat(parsed.grade()).isEqualTo(RiskGrade.HIGH);
        assertThat(parsed.provisional()).isTrue();
    }

    @Test
    @DisplayName("노출 없음 등급을 읽는다")
    void readsNone() {
        ReportParser.ParsedReport parsed = ReportParser.parse("위험 등급: 노출 없음\n\n사람 검토: 불필요. 확인할 항목이 없다.");
        assertThat(parsed.grade()).isEqualTo(RiskGrade.NONE);
        assertThat(parsed.reviewRequired()).isFalse();
        assertThat(parsed.reviewReason()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "사람 검토: 불필요.",
            "위험 등급: 상",
            "위험 등급: 매우 높음\n사람 검토: 불필요.",
            "위험도는 상입니다. 사람 검토는 필요 없습니다."
    })
    @DisplayName("BR02: 등급이나 사람 검토 줄을 읽지 못하면 UNKNOWN + 사람 검토 필요로 둔다(추측하지 않음)")
    void unknownWhenUnreadable(String report) {
        ReportParser.ParsedReport parsed = ReportParser.parse(report);
        assertThat(parsed.grade()).isEqualTo(RiskGrade.UNKNOWN);
        assertThat(parsed.reviewRequired()).isTrue();
        assertThat(parsed.parsed()).isFalse();
    }
}
