package com.imbank.docguard.result;

import java.util.ArrayList;
import java.util.List;

/**
 * 화면에 기본으로 보여 줄 표시값을 만듭니다 (DOMAIN.md BR09).
 * Studio 가 이미 일부 가림·전체 가림으로 판정한 값은 원문 그대로(이미 가려진 상태로) 보여 줍니다.
 */
public final class DisplayValueMasker {

    private DisplayValueMasker() {
    }

    public static String mask(String rawValue, String infoType, String maskState) {
        if (rawValue == null || rawValue.isBlank()) {
            return "";
        }
        if ("부분마스킹".equals(maskState) || "전체마스킹".equals(maskState)) {
            return rawValue;
        }
        String value = rawValue.strip();
        return switch (infoType == null ? "" : infoType) {
            case "이름" -> keepFirst(value, 1);
            case "주민등록번호", "외국인등록번호" -> keepLeadingDigits(value, 6);
            case "여권번호", "운전면허번호" -> keepFirst(value, 2);
            case "계좌번호", "카드번호", "전화번호" -> keepTrailingDigits(value, 4);
            case "이메일" -> email(value);
            case "주소" -> address(value);
            case "예금잔액", "거래내역", "소득", "재산", "채무", "대출신청금액" -> "***,***원";
            default -> "(가림)";
        };
    }

    /** 앞 n 글자만 남기고 나머지 글자·숫자를 *로 바꿉니다. 공백과 기호는 남깁니다. */
    static String keepFirst(String value, int keep) {
        StringBuilder sb = new StringBuilder();
        int kept = 0;
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            if (Character.isLetterOrDigit(cp) && kept >= keep) {
                sb.append('*');
            } else {
                if (Character.isLetterOrDigit(cp)) {
                    kept++;
                }
                sb.appendCodePoint(cp);
            }
            i += Character.charCount(cp);
        }
        return sb.toString();
    }

    /** 앞 n 개 숫자만 남기고 나머지 숫자를 *로 바꿉니다. 구분 기호(-)는 남깁니다. */
    static String keepLeadingDigits(String value, int keep) {
        StringBuilder sb = new StringBuilder();
        int seen = 0;
        for (char c : value.toCharArray()) {
            if (Character.isDigit(c)) {
                sb.append(seen < keep ? c : '*');
                seen++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 마지막 n 개 숫자만 남기고 나머지 숫자를 *로 바꿉니다. */
    static String keepTrailingDigits(String value, int keep) {
        long digits = value.chars().filter(Character::isDigit).count();
        StringBuilder sb = new StringBuilder();
        int seen = 0;
        for (char c : value.toCharArray()) {
            if (Character.isDigit(c)) {
                sb.append(seen >= digits - keep ? c : '*');
                seen++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    static String email(String value) {
        int at = value.indexOf('@');
        if (at <= 0) {
            return keepFirst(value, 2);
        }
        String local = value.substring(0, at);
        return local.substring(0, Math.min(2, local.length())) + "***" + value.substring(at);
    }

    /** 시·도와 시·군·구까지만 남깁니다. 예: "대구광역시 수성구 ***" */
    static String address(String value) {
        String[] tokens = value.split("\\s+");
        List<String> kept = new ArrayList<>();
        for (String token : tokens) {
            if (kept.size() < 3 && token.matches(".*(도|시|구|군)$")) {
                kept.add(token);
            } else {
                break;
            }
        }
        if (kept.isEmpty()) {
            kept.add(tokens[0]);
        }
        return String.join(" ", kept) + " ***";
    }
}
