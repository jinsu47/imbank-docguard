package com.imbank.docguard.common;

import org.springframework.http.HttpStatus;

/**
 * 화면에 돌려줄 오류. {@code code}는 화면이 분기에 쓰는 고정 문자열이고, {@code message}는 사용자에게 보여 줄 한국어 문장입니다.
 * 메시지에 원문값이나 파일 이름을 넣지 않습니다. (SECURITY.md 7장)
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "찾을 수 없습니다.");
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "권한이 없습니다.");
    }

    public static ApiException tooManyRequests(String message) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", message);
    }
}
