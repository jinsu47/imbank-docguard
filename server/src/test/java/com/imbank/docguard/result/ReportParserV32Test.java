package com.imbank.docguard.result;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imbank.docguard.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** v3.2 보고서 해석: 같은 제목 반복, 미확정 등급, 화면 표시 용어. */
class ReportParserV32Test {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private List<Path> reports() throws IOException {
        Path dir = TestFixtures.requireEval(TestFixtures.V32_REPORTS);
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(p -> p.getFileName().toString().endsWith("분석보고서.json")).sorted().toList();
        }
    }

    private String content(Path report) throws IOException {
        return objectMapper.readTree(report.toFile()).path("content").asText();
    }

    private String firstRun(String tag) throws IOException {
        Path report = reports().stream()
                .filter(p -> p.getFileName().toString().contains(tag + "_") && !p.toString().contains("2회차"))
                .findFirst().orElseThrow();
        return content(report);
    }

    private static String tagOf(Path report) {
        Matcher m = Pattern.compile("set\\d+").matcher(report.getFileName().toString());
        return m.find() ? m.group() : "";
    }

    @Test
    @DisplayName("같은 제목이 반복되면 문단을 버리지 않고 나온 순서대로 줄바꿈으로 잇는다")
    void joinsRepeatedTitles() {
        ReportParser.ParsedReport parsed = ReportParser.parse("""
                위험 등급: 상

                사람 검토: 불필요. 없음

                판정 근거: 첫 근거【†1】

                판정 근거: 둘째 근거【†2】
                판정 근거: 셋째 근거
                점검 범위: 전체
                """);

        assertThat(parsed.sections().get("판정 근거")).isEqualTo("첫 근거【†1】\n둘째 근거【†2】\n셋째 근거");
        assertThat(parsed.sections().get("점검 범위")).isEqualTo("전체");
    }

    @Test
    @DisplayName("미확정은 읽기 실패(UNKNOWN)와 구분해 UNDECIDED 로 두고, 사람 검토 사유는 보고서대로 읽는다")
    void undeterminedGrade() {
        ReportParser.ParsedReport parsed = ReportParser.parse("""
                위험 등급: 미확정

                사람 검토: 필요. 점검 범위를 확인하지 못했다.

                요약: 판정할 수 없다.
                """);

        assertThat(parsed.parsed()).isTrue();
        assertThat(parsed.grade()).isEqualTo(RiskGrade.UNDECIDED);
        assertThat(parsed.grade().label()).isEqualTo("미확정");
        assertThat(parsed.reviewRequired()).isTrue();
        assertThat(parsed.reviewReason()).isEqualTo("점검 범위를 확인하지 못했다.");
    }

    @Test
    @DisplayName("제목 반복 처리 전에 잘려 저장된 결과도 저장 원문에서 다시 읽어 문단을 모두 보여 준다")
    void storedResultsAreReparsedFromRaw() {
        String raw = "위험 등급: 상\n\n사람 검토: 불필요. 없음\n\n판정 근거: 첫 근거\n\n판정 근거: 둘째 근거\n";
        ResultPayload.Report truncated = new ResultPayload.Report(raw, java.util.Map.of("판정 근거", "첫 근거"), "");

        assertThat(AnalysisResultService.displaySections(truncated).get("판정 근거")).isEqualTo("첫 근거\n둘째 근거");
    }

    @ParameterizedTest(name = "{0}: 판정 근거 {1}회")
    @CsvSource({"set02, 2", "set04, 3"})
    @DisplayName("v3.2 시험 자료에서 반복된 판정 근거가 모두 남는다")
    void benchmarkKeepsEveryParagraph(String tag, int times) throws IOException {
        String report = firstRun(tag);
        Matcher heading = Pattern.compile("(?m)^\\s*판정 근거\\s*:\\s*([^\\n]*)").matcher(report);
        int found = 0;
        String body = ReportParser.parse(report).sections().get("판정 근거");
        while (heading.find()) {
            found++;
            assertThat(body).contains(heading.group(1).strip());
        }
        assertThat(found).isEqualTo(times);
        assertThat(body.lines().count()).isGreaterThanOrEqualTo(times);
    }

    @Test
    @DisplayName("v3.2 시험 자료 20건의 등급과 사람 검토가 정답표와 같다")
    void benchmarkMatchesAnswerKey() throws IOException {
        JsonNode docs = objectMapper.readTree(TestFixtures.requireEval(TestFixtures.ANSWER_KEY_V3).toFile()).path("docs");
        List<Path> reports = reports();
        assertThat(reports).hasSize(20);
        for (Path report : reports) {
            String tag = tagOf(report);
            JsonNode doc = docs.path(tag);
            JsonNode key = doc.has("same_as") ? docs.path(doc.path("same_as").asText()) : doc;
            ReportParser.ParsedReport parsed = ReportParser.parse(content(report));
            assertThat(parsed.parsed()).as(tag).isTrue();
            assertThat(parsed.grade().label()).as(tag + " 등급").isEqualTo(key.path("grade").asText());
            assertThat(parsed.reviewRequired() ? "필요" : "불필요").as(tag + " 사람 검토").isEqualTo(key.path("review").asText());
        }
    }

    @Test
    @DisplayName("화면 표시에서 내부 가림 용어를 쉬운 말로 바꾸고 인용 표시는 유지한다")
    void displayText() {
        assertThat(ReportDisplayText.of("미마스킹 값【†1】, 부분마스킹 값, 전체마스킹 값【†12】, 전체 마스킹"))
                .isEqualTo("가림 없음 값【†1】, 일부 가림 값, 전체 가림 값【†12】, 전체 가림");
        assertThat(ReportDisplayText.of(null)).isNull();
    }

    @Test
    @DisplayName("v3.2 시험 자료 set03 의 내부 용어가 화면 문장에서 사라진다")
    void benchmarkDisplayText() throws IOException {
        String report = firstRun("set03");
        assertThat(report).contains("전체마스킹");

        String shown = ReportParser.parse(report).sections().values().stream()
                .map(ReportDisplayText::of).reduce("", String::concat);

        assertThat(shown).doesNotContain("전체마스킹").contains("전체 가림");
    }
}
