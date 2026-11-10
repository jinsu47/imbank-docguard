package com.imbank.docguard.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imbank.docguard.common.ErrorResponse;
import com.imbank.docguard.common.RateLimiter;
import com.imbank.docguard.common.RequestIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * 그 밖의 API 속도 제한 (SECURITY.md 8장): 사용자당(로그인 전에는 IP 당) 분당 300회.
 * 로그인, 가입, 분석 생성, 원문 보기는 서비스에서 따로 더 좁게 제한합니다.
 */
class ApiRateLimitFilter extends OncePerRequestFilter {

    static final int LIMIT_PER_MINUTE = 300;

    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    ApiRateLimitFilter(RateLimiter rateLimiter, ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String key = auth != null && auth.getPrincipal() instanceof AuthUser user
                ? "api-user:" + user.id()
                : "api-ip:" + request.getRemoteAddr();
        if (!rateLimiter.tryAcquire(key, LIMIT_PER_MINUTE, Duration.ofMinutes(1))) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Retry-After", "60");
            objectMapper.writeValue(response.getOutputStream(), new ErrorResponse("RATE_LIMITED",
                    "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.", RequestIdFilter.current()));
            return;
        }
        chain.doFilter(request, response);
    }
}
