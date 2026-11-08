package com.imbank.docguard.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imbank.docguard.audit.AuditAction;
import com.imbank.docguard.audit.AuditService;
import com.imbank.docguard.common.AppProperties;
import com.imbank.docguard.common.ErrorResponse;
import com.imbank.docguard.common.RateLimiter;
import com.imbank.docguard.common.RequestIdFilter;
import com.imbank.docguard.user.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

import java.io.IOException;

/**
 * 보안 설정 (SECURITY.md 2장, 3장).
 * <ul>
 *   <li>세션 쿠키(DOCGUARD_SESSION) + CSRF 쿠키(XSRF-TOKEN, 헤더 X-XSRF-TOKEN)</li>
 *   <li>역할별 경로 규칙. 분석 단위의 소유자·검토 범위 확인은 서비스의 AnalysisAccessPolicy 가 합니다.</li>
 *   <li>인증이 없으면 401, 권한이 없으면 403 을 JSON 으로 돌려줍니다.</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository(AppProperties properties) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie
                .path("/")
                .sameSite("Lax")
                .secure(properties.cookieSecure()));
        return repository;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contextRepository,
                                            CsrfTokenRepository csrfTokenRepository, AuditService audit,
                                            ObjectMapper objectMapper, UserRepository users,
                                            RateLimiter rateLimiter) throws Exception {
        http
                .addFilterBefore(new MultipartGuardFilter(objectMapper), CsrfFilter.class)
                // 세션 사용자의 현재 상태를 반영한 뒤(비활성화·역할 변경) 경로 규칙을 확인하고, 그다음 속도 제한을 셉니다.
                .addFilterBefore(new SessionUserRefreshFilter(users, contextRepository), AuthorizationFilter.class)
                .addFilterAfter(new ApiRateLimitFilter(rateLimiter, objectMapper), SessionUserRefreshFilter.class)
                .securityContext(context -> context.securityContextRepository(contextRepository))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").denyAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login", "/api/auth/logout")
                        .permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/reviews/**").hasAnyRole("REVIEWER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/analyses/*/review").hasAnyRole("REVIEWER", "ADMIN")
                        .requestMatchers("/api/analyses", "/api/analyses/**").hasAnyRole("INSPECTOR", "REVIEWER", "ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        // API 가 아닌 GET 은 React 빌드(화면 코드)입니다. 문서·결과는 모두 위의 /api 규칙을 거칩니다.
                        .requestMatchers(HttpMethod.GET, "/**").permitAll()
                        .anyRequest().denyAll())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .addLogoutHandler((request, response, authentication) -> {
                            if (authentication != null && authentication.getPrincipal() instanceof AuthUser user) {
                                audit.record(AuditAction.LOGOUT, user.id());
                            }
                        })
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                        .deleteCookies("DOCGUARD_SESSION"))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) ->
                                writeError(response, objectMapper, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다."))
                        .accessDeniedHandler((request, response, e) -> {
                            if (e instanceof CsrfException) {
                                writeError(response, objectMapper, HttpStatus.FORBIDDEN, "CSRF_INVALID",
                                        "보안 토큰이 만료되었습니다. 화면을 새로 고친 뒤 다시 시도해 주세요.");
                            } else {
                                writeError(response, objectMapper, HttpStatus.FORBIDDEN, "FORBIDDEN", "권한이 없습니다.");
                            }
                        }))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; img-src 'self' blob: data:; worker-src 'self' blob:"))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER)));
        return http.build();
    }

    private static void writeError(HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status,
                                   String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(code, message, RequestIdFilter.current()));
    }
}
