package com.imbank.docguard.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imbank.docguard.common.ErrorResponse;
import com.imbank.docguard.common.RequestIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

/**
 * 파일 업로드 주소 외의 multipart 요청을 본문을 읽기 전에 거절합니다 (SECURITY.md 4장).
 * 업로드 크기를 제한하지 않으므로, 로그인 없이 열려 있는 주소(화면, 로그인 등)로 보낸 multipart 본문이
 * 서버 임시 폴더에 저장되어 디스크를 채우는 일을 막습니다.
 */
final class MultipartGuardFilter extends OncePerRequestFilter {

    private static final String UPLOAD_PATH = "/api/analyses";

    private final ObjectMapper objectMapper;

    MultipartGuardFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    static boolean isMultipart(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean upload = HttpMethod.POST.matches(request.getMethod()) && UPLOAD_PATH.equals(request.getRequestURI());
        if (isMultipart(request) && !upload) {
            response.setStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getOutputStream(), new ErrorResponse("MULTIPART_NOT_ALLOWED",
                    "지원하지 않는 요청 형식입니다.", RequestIdFilter.current()));
            return;
        }
        chain.doFilter(request, response);
    }
}
