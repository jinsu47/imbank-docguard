package com.imbank.docguard.studio;

/**
 * Studio 호출 실패. 메시지에는 Studio 응답 본문을 넣지 않고 상태 코드와 오류 코드만 둡니다. (SECURITY.md 7장)
 */
public class StudioException extends RuntimeException {

    private final String code;
    private final boolean retryable;

    public StudioException(String code, boolean retryable, String message) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }

    /** 예: STUDIO_NOT_CONFIGURED, STUDIO_HTTP_429, STUDIO_HTTP_500, STUDIO_NETWORK, STUDIO_BAD_RESPONSE */
    public String code() {
        return code;
    }

    /** 429, 5xx, 네트워크 오류처럼 다시 시도할 만한 실패인지 */
    public boolean retryable() {
        return retryable;
    }
}
