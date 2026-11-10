package com.imbank.docguard.common;

/** 오류 응답 형식 (ARCHITECTURE.md 5장) */
public record ErrorResponse(String code, String message, String requestId) {
}
