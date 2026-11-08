package com.imbank.docguard.result;

/** 위험 등급 (DOMAIN.md 1장). UNKNOWN 은 서버가 보고서를 해석하지 못한 경우에만 씁니다. */
public enum RiskGrade {
    HIGH("상"),
    MEDIUM("중"),
    LOW("하"),
    NONE("노출 없음"),
    UNKNOWN("판정 확인 필요");

    private final String label;

    RiskGrade(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    static RiskGrade fromReportLabel(String label) {
        return switch (label) {
            case "상" -> HIGH;
            case "중" -> MEDIUM;
            case "하" -> LOW;
            case "노출 없음" -> NONE;
            default -> UNKNOWN;
        };
    }
}
