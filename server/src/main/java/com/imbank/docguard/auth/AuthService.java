package com.imbank.docguard.auth;

import com.imbank.docguard.audit.AuditAction;
import com.imbank.docguard.audit.AuditService;
import com.imbank.docguard.common.ApiException;
import com.imbank.docguard.common.AppProperties;
import com.imbank.docguard.common.RateLimiter;
import com.imbank.docguard.user.Role;
import com.imbank.docguard.user.User;
import com.imbank.docguard.user.UserRepository;
import com.imbank.docguard.user.UserStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 회원가입과 세션 로그인 (F01, SECURITY.md 2장).
 * 실패 문구는 계정이 있든 없든 같게 해서 계정 존재 여부를 알리지 않습니다. 잠금 안내만 따로 보여 줍니다(USER_FLOW.md 3장).
 */
@Service
public class AuthService {

    static final String INVALID_MESSAGE = "이메일 또는 비밀번호가 맞지 않습니다.";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final RateLimiter rateLimiter;
    private final TransactionTemplate tx;
    private final SecurityContextRepository contextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final AppProperties properties;
    /** 없는 계정으로 로그인할 때도 같은 시간이 걸리도록 비교에 쓰는 해시 */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, AuditService audit, RateLimiter rateLimiter,
                       TransactionTemplate tx, SecurityContextRepository contextRepository,
                       CsrfTokenRepository csrfTokenRepository, AppProperties properties) {
        this.properties = properties;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.rateLimiter = rateLimiter;
        this.tx = tx;
        this.contextRepository = contextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public AuthUser signup(AuthDtos.SignupRequest request, String clientIp) {
        if (!properties.signupEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SIGNUP_CLOSED", "회원가입을 받지 않습니다. 관리자에게 계정을 요청해 주세요.");
        }
        rateLimiter.check("signup-ip:" + clientIp, 10, Duration.ofHours(1), "가입 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.");
        String email = User.normalizeEmail(request.email());
        PasswordPolicy.validate(request.password(), email);
        User saved;
        try {
            saved = tx.execute(status -> {
                if (users.existsByEmail(email)) {
                    throw ApiException.conflict("EMAIL_TAKEN", "이미 가입한 이메일입니다.");
                }
                return users.save(new User(email, request.displayName(), passwordEncoder.encode(request.password()),
                        Role.INSPECTOR));
            });
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("EMAIL_TAKEN", "이미 가입한 이메일입니다.");
        }
        audit.record(AuditAction.SIGNUP, saved.getId());
        return AuthUser.from(saved);
    }

    public AuthUser login(AuthDtos.LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        rateLimiter.check("login-ip:" + httpRequest.getRemoteAddr(), 20, Duration.ofMinutes(1),
                "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.");
        String email = User.normalizeEmail(request.email());
        LoginOutcome outcome = tx.execute(status -> attempt(email, request.password()));

        switch (outcome.kind()) {
            case INVALID -> {
                audit.record(AuditAction.LOGIN_FAILURE, outcome.userId());
                throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", INVALID_MESSAGE);
            }
            case LOCKED_NOW, LOCKED -> {
                audit.record(outcome.kind() == Kind.LOCKED_NOW ? AuditAction.ACCOUNT_LOCKED : AuditAction.LOGIN_FAILURE,
                        outcome.userId());
                throw new ApiException(HttpStatus.LOCKED, "ACCOUNT_LOCKED",
                        "로그인을 5회 연속 실패해 계정이 15분 동안 잠겼습니다. 잠시 후 다시 시도해 주세요.");
            }
            default -> {
                // 세션 고정 공격을 막기 위해 세션 ID 를 새로 발급하고, CSRF 토큰도 새로 만듭니다.
                httpRequest.getSession(true);
                httpRequest.changeSessionId();
                AuthUser user = outcome.user();
                var token = new UsernamePasswordAuthenticationToken(user, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(token);
                SecurityContextHolder.setContext(context);
                contextRepository.saveContext(context, httpRequest, httpResponse);
                csrfTokenRepository.saveToken(null, httpRequest, httpResponse);
                CsrfToken fresh = csrfTokenRepository.generateToken(httpRequest);
                csrfTokenRepository.saveToken(fresh, httpRequest, httpResponse);
                audit.record(AuditAction.LOGIN_SUCCESS, user.id());
                return user;
            }
        }
    }

    private LoginOutcome attempt(String email, String password) {
        Optional<User> found = users.findByEmail(email);
        if (found.isEmpty()) {
            passwordEncoder.matches(password, dummyHash);
            return new LoginOutcome(Kind.INVALID, null, null);
        }
        User user = found.get();
        Instant now = Instant.now();
        if (user.getStatus() == UserStatus.DISABLED) {
            passwordEncoder.matches(password, dummyHash);
            return new LoginOutcome(Kind.INVALID, user.getId(), null);
        }
        if (user.refreshLock(now)) {
            return new LoginOutcome(Kind.LOCKED, user.getId(), null);
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            boolean lockedNow = user.recordLoginFailure(now);
            return new LoginOutcome(lockedNow ? Kind.LOCKED_NOW : Kind.INVALID, user.getId(), null);
        }
        user.recordLoginSuccess(now);
        return new LoginOutcome(Kind.SUCCESS, user.getId(), AuthUser.from(user));
    }

    private enum Kind { SUCCESS, INVALID, LOCKED, LOCKED_NOW }

    private record LoginOutcome(Kind kind, Long userId, AuthUser user) {
    }
}
