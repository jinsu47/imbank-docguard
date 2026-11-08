package com.imbank.docguard.auth;

import com.imbank.docguard.audit.AuditService;
import com.imbank.docguard.common.ApiException;
import com.imbank.docguard.common.AppProperties;
import com.imbank.docguard.common.RateLimiter;
import com.imbank.docguard.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** 공개 배포에서는 회원가입을 끕니다(APP_SIGNUP_ENABLED=false). 화면에서 숨기는 것과 별개로 서버가 거절해야 합니다. */
class AuthServiceSignupClosedTest {

    @Test
    @DisplayName("가입을 닫으면 요청 내용과 상관없이 403 으로 거절하고 계정을 만들지 않는다")
    void rejectsWhenClosed() {
        UserRepository users = mock(UserRepository.class);
        AuthService service = new AuthService(users, mock(PasswordEncoder.class), mock(AuditService.class),
                mock(RateLimiter.class), mock(TransactionTemplate.class), mock(SecurityContextRepository.class),
                mock(CsrfTokenRepository.class), new AppProperties(null, "storage", 168, true, false));

        assertThatThrownBy(() -> service.signup(null, "127.0.0.1"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(e.code()).isEqualTo("SIGNUP_CLOSED");
                });
        verifyNoInteractions(users);
    }
}
