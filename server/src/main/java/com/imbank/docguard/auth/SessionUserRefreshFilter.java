package com.imbank.docguard.auth;

import com.imbank.docguard.user.User;
import com.imbank.docguard.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * 요청마다 세션 사용자의 현재 상태를 DB 에서 다시 확인합니다 (DOMAIN.md 5장 "세션 즉시 만료").
 * <ul>
 *   <li>관리자가 비활성화했거나 잠근 계정이면 세션을 끊습니다. 이후 규칙에 따라 401 이 됩니다.</li>
 *   <li>역할이 바뀌었으면 세션의 권한을 새 역할로 바꿉니다.</li>
 * </ul>
 * 로그인 실패로 잠긴 계정(해제 시각 있음)은 끊지 않습니다. 남이 틀린 비밀번호를 넣어 로그인 중인 사용자를 내보낼 수 없게 하기 위해서입니다.
 * SecurityConfig 에서만 등록합니다(@Component 로 두면 서블릿 필터로 한 번 더 등록됨).
 */
class SessionUserRefreshFilter extends OncePerRequestFilter {

    private final UserRepository users;
    private final SecurityContextRepository contextRepository;

    SessionUserRefreshFilter(UserRepository users, SecurityContextRepository contextRepository) {
        this.users = users;
        this.contextRepository = contextRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser current) {
            Optional<User> found = users.findById(current.id());
            if (found.isEmpty() || found.get().blockedByAdmin()) {
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
            } else if (found.get().getRole() != current.role()) {
                AuthUser updated = AuthUser.from(found.get());
                var token = new UsernamePasswordAuthenticationToken(updated, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + updated.role().name())));
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(token);
                SecurityContextHolder.setContext(context);
                contextRepository.saveContext(context, request, response);
            }
        }
        chain.doFilter(request, response);
    }
}
