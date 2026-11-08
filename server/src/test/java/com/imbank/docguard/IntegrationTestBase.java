package com.imbank.docguard;

import com.imbank.docguard.auth.AuthUser;
import com.imbank.docguard.common.RateLimiter;
import com.imbank.docguard.user.Role;
import com.imbank.docguard.user.User;
import com.imbank.docguard.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.support.WebTestUtils;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * 통합 테스트 공통 설정. PostgreSQL 16 컨테이너 하나를 모든 통합 테스트가 같이 씁니다.
 * <ul>
 *   <li>Docker 가 꺼져 있으면 건너뜁니다(SKIPPED). 그때는 Docker Desktop 을 먼저 켭니다.</li>
 *   <li>암호화 키는 실행할 때마다 무작위로 만들고, 원본 저장 위치는 임시 폴더입니다.</li>
 *   <li>Studio 는 재생 모드(저장 응답, 지연 0)로 두고 실제 Upstage API 를 부르지 않습니다. 스케줄러는 끄고 처리기를 직접 부릅니다.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
public abstract class IntegrationTestBase {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static final Path STORAGE;

    static {
        try {
            STORAGE = Files.createTempDirectory("docguard-storage");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.encryption-key", TestFixtures::encryptionKey);
        registry.add("app.storage-dir", STORAGE::toString);
        registry.add("app.scheduling.enabled", () -> "false");
        registry.add("upstage.mode", () -> "replay");
        registry.add("upstage.replay-dir", TestFixtures.API_RAW_DIR::toString);
        registry.add("upstage.replay-step-ms", () -> "0");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected UserRepository users;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected RateLimiter rateLimiter;

    @BeforeEach
    void resetRateLimits() {
        rateLimiter.clear();
    }

    /** 무작위 이메일로 사용자를 만듭니다. 비밀번호는 테스트마다 무작위입니다. */
    protected AuthUser createUser(Role role) {
        return createUser(role, TestFixtures.password());
    }

    protected AuthUser createUser(Role role, String password) {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.local";
        User user = users.save(new User(email, "시험 " + role.name(), passwordEncoder.encode(password), role));
        return AuthUser.from(user);
    }

    /** MockMvc 요청을 이 사용자로 보냅니다. */
    protected static RequestPostProcessor as(AuthUser user) {
        return authentication(new UsernamePasswordAuthenticationToken(user, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name()))));
    }

    /**
     * 파일 업로드용 CSRF. 업로드는 헤더로만 토큰을 받으므로, 화면처럼 원래 토큰 값을 헤더로 보냅니다.
     * csrf() 는 공유 필터의 저장소를 시험용으로 바꾸고 가공한 토큰을 파라미터로 넣으므로, 그 저장소에서 원래 값을 꺼내 씁니다.
     */
    protected static RequestPostProcessor csrfHeader() {
        return request -> {
            MockHttpServletRequest processed = csrf().postProcessRequest(request);
            CsrfToken token = WebTestUtils.getCsrfTokenRepository(processed).loadToken(processed);
            processed.removeParameter(token.getParameterName());
            processed.addHeader(token.getHeaderName(), token.getToken());
            return processed;
        };
    }
}
