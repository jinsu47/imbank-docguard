package com.imbank.docguard.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

/**
 * SPA 용 CSRF 처리 (Spring Security 문서의 권장 방식).
 * 화면은 XSRF-TOKEN 쿠키 값을 그대로 X-XSRF-TOKEN 헤더에 넣으므로, 헤더 값은 원래 값으로 비교합니다.
 */
final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
        xor.handle(request, response, csrfToken);
        // 매 요청 토큰을 불러와 쿠키가 항상 있도록 합니다.
        csrfToken.get();
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        String header = request.getHeader(csrfToken.getHeaderName());
        if (StringUtils.hasText(header)) {
            return plain.resolveCsrfTokenValue(request, csrfToken);
        }
        // 헤더가 없을 때 요청 파라미터를 읽으면 Tomcat 이 multipart 본문 전체를 임시 파일에 먼저 저장합니다.
        // 업로드 크기를 제한하지 않으므로, 로그인하지 않은 요청이 디스크를 채우지 못하게 파일 업로드는 헤더로만 받습니다.
        if (MultipartGuardFilter.isMultipart(request)) {
            return null;
        }
        return xor.resolveCsrfTokenValue(request, csrfToken);
    }
}
