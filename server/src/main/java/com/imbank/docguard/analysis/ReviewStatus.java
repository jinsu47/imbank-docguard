package com.imbank.docguard.analysis;

/** 검토 상태 (DOMAIN.md 5장). 분석이 COMPLETED 가 될 때 정해집니다. */
public enum ReviewStatus {
    NOT_REQUIRED,
    PENDING,
    CONFIRMED,
    DISPUTED
}
