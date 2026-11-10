package com.imbank.docguard.result;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Studio 보고서에서 등급, 사람 검토 여부, 항목별 문단을 읽습니다 (ARCHITECTURE.md 4장 "보고서 해석 규칙").
 * 규칙은 {@code eval/compare.py}의 {@code parse_report}와 같습니다.
 * <ul>
 *   <li>{@code 위험 등급: (잠정 )?(상|중|하|미확정|노출 없음)} 줄에서 등급을 읽습니다.</li>
 *   <li>{@code 사람 검토: (필요|불필요)} 줄에서 사람 검토 여부를 읽습니다.</li>
 *   <li>어느 줄이든 읽지 못하면 등급 UNKNOWN, 사람 검토 필요로 둡니다(BR02). 등급을 추측하지 않습니다.</li>
 * </ul>
 */
public final class ReportParser {

    /** 보고서 항목 제목. 이 순서로 화면에 보여 줍니다. */
    public static final List<String> SECTION_TITLES = List.of(
            "요약", "발견된 문제", "판정 근거", "정책 연결 상태", "적용한 공식 근거", "활용 맥락", "추가 확인 및 조치", "점검 범위");

    private static final Pattern CITATION = Pattern.compile("【†\\d+】");
    private static final Pattern GRADE = Pattern.compile("위험 등급\\s*[:\\n]\\s*(잠정\\s*)?(노출 없음|미확정|상|중|하)");
    private static final Pattern REVIEW = Pattern.compile("사람 검토\\s*[:\\n]\\s*(불필요|필요)\\.?\\s*([^\\n]*)");
    private static final Pattern SECTION = Pattern.compile(
            "(?m)^\\s*(" + String.join("|", SECTION_TITLES) + ")\\s*:\\s*");

    private ReportParser() {
    }

    /**
     * @param grade          읽은 등급. 읽지 못하면 UNKNOWN
     * @param provisional    "잠정"이 붙었는지
     * @param reviewRequired 사람 검토 필요 여부. 읽지 못하면 true
     * @param reviewReason   "사람 검토: 필요." 뒤의 사유 문장(인용 표시 포함). 없으면 빈 문자열
     * @param sections       항목 제목 → 문단(인용 표시 【†n】은 남겨 둠)
     * @param parsed         등급과 사람 검토 줄을 모두 읽었는지
     */
    public record ParsedReport(RiskGrade grade, boolean provisional, boolean reviewRequired, String reviewReason,
                               Map<String, String> sections, boolean parsed) {
    }

    public static ParsedReport parse(String report) {
        if (report == null || report.isBlank()) {
            return new ParsedReport(RiskGrade.UNKNOWN, false, true, "", Map.of(), false);
        }
        String plain = CITATION.matcher(report).replaceAll("");
        Matcher grade = GRADE.matcher(plain);
        Matcher review = REVIEW.matcher(report);
        Matcher reviewPlain = REVIEW.matcher(plain);
        boolean gradeFound = grade.find();
        boolean reviewFound = reviewPlain.find();
        review.find();

        Map<String, String> sections = sections(report);
        if (!gradeFound || !reviewFound) {
            return new ParsedReport(RiskGrade.UNKNOWN, false, true, "", sections, false);
        }
        boolean provisional = grade.group(1) != null && !grade.group(1).isBlank();
        boolean required = "필요".equals(reviewPlain.group(1));
        String reason = required && review.groupCount() >= 2 && review.group(2) != null ? review.group(2).strip() : "";
        return new ParsedReport(RiskGrade.fromReportLabel(grade.group(2)), provisional, required, reason, sections, true);
    }

    private static Map<String, String> sections(String report) {
        Map<String, String> result = new LinkedHashMap<>();
        Matcher m = SECTION.matcher(report);
        String title = null;
        int start = 0;
        while (m.find()) {
            if (title != null) {
                append(result, title, report.substring(start, m.start()));
            }
            title = m.group(1);
            start = m.end();
        }
        if (title != null) {
            append(result, title, report.substring(start));
        }
        return result;
    }

    /** 모델이 같은 제목을 반복해도 문단을 버리지 않고 나온 순서대로 줄바꿈으로 잇습니다. */
    private static void append(Map<String, String> sections, String title, String body) {
        sections.merge(title, body.strip(),
                (first, next) -> first.isEmpty() ? next : next.isEmpty() ? first : first + "\n" + next);
    }
}
