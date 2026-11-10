package com.imbank.docguard.result;

import java.util.regex.Pattern;

/**
 * 보고서 문장을 화면에 줄 때 내부 용어를 쉬운 말로 바꿉니다. 저장된 원문과 【†n】 인용 표시는 그대로 둡니다.
 */
final class ReportDisplayText {

    private static final Pattern MASK_TERM = Pattern.compile("(미|부분|전체)\\s?마스킹");

    private ReportDisplayText() {
    }

    static String of(String text) {
        if (text == null) {
            return null;
        }
        return MASK_TERM.matcher(text).replaceAll(match -> switch (match.group(1)) {
            case "미" -> "가림 없음";
            case "부분" -> "일부 가림";
            default -> "전체 가림";
        });
    }
}
