package com.imbank.docguard.user;

/** 역할 (SECURITY.md 1장) */
public enum Role {
    /** 점검 담당자: 문서를 올리고 자기 결과를 봄 */
    INSPECTOR,
    /** 검토자: 사람 검토가 필요한 문서를 확인하고 결정을 기록 */
    REVIEWER,
    /** 관리자: 계정과 감사 기록 관리. 문서 내용은 볼 수 없음 */
    ADMIN
}
