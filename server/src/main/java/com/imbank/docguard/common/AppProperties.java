package com.imbank.docguard.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.*} 설정 (ARCHITECTURE.md 7장).
 *
 * @param encryptionKey  AES-256 키(32바이트 Base64). 기본값을 두지 않으며, 없으면 서버가 시작되지 않습니다.
 * @param storageDir     원본 파일 저장 위치
 * @param retentionHours 이 시간이 지나면 관리자에게 삭제를 권고합니다(자동으로 지우지 않음)
 * @param cookieSecure   세션·CSRF 쿠키에 Secure 를 붙일지
 * @param signupEnabled  회원가입을 받을지. 공개 배포에서는 끕니다.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String encryptionKey,
        String storageDir,
        int retentionHours,
        boolean cookieSecure,
        boolean signupEnabled) {
}
