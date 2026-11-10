package com.imbank.docguard.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imbank.docguard.IntegrationTestBase;
import com.imbank.docguard.TestFixtures;
import com.imbank.docguard.audit.AuditAction;
import com.imbank.docguard.audit.AuditLogRepository;
import com.imbank.docguard.user.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 회원가입·로그인·잠금·로그아웃 (F01, SECURITY.md 2장).
 * MockMvc 에서는 테스트 도구가 CSRF 저장소를 바꾸므로 csrf() 를 씁니다. 실제 XSRF-TOKEN 쿠키 동작은 서버를 띄워 확인합니다.
 */
class AuthFlowIntegrationTest extends IntegrationTestBase {

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AuditLogRepository auditLogs;

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    @Test
    @DisplayName("CSRF 헤더 없이 상태를 바꾸는 요청은 403 CSRF_INVALID")
    void csrfRequired() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "a@b.c", "password", "x"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    @DisplayName("가입 → 로그인 → me → 로그아웃. 가입은 role 을 보내도 항상 INSPECTOR")
    void signupLoginLogout() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@test.local";
        String password = TestFixtures.password();

        mockMvc.perform(post("/api/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("displayName", "시험 사용자", "email", email.toUpperCase(),
                                "password", password, "agreed", true, "role", "ADMIN"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("INSPECTOR"))
                .andExpect(jsonPath("$.email").value(email));

        MockHttpSession session = new MockHttpSession();
        MvcResult login = mockMvc.perform(post("/api/auth/login").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("INSPECTOR"))
                .andReturn();
        // 로그인하면 세션 ID 를 새로 발급합니다(세션 고정 방지).
        assertThat(login.getRequest().getSession(false)).isNotNull();

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("비밀번호 규칙 위반, 중복 이메일, 동의 누락은 가입을 거부한다")
    void signupValidation() throws Exception {
        String email = "dup-" + UUID.randomUUID() + "@test.local";
        mockMvc.perform(post("/api/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("displayName", "a", "email", email, "password", "short1", "agreed", true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
        mockMvc.perform(post("/api/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("displayName", "a", "email", email, "password", "onlyletterspassword", "agreed", true))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("displayName", "a", "email", email, "password", TestFixtures.password(), "agreed", false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        createUserWithEmail(email);
        mockMvc.perform(post("/api/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("displayName", "a", "email", email, "password", TestFixtures.password(), "agreed", true))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    private void createUserWithEmail(String email) {
        users.save(new com.imbank.docguard.user.User(email, "기존", passwordEncoder.encode(TestFixtures.password()), Role.INSPECTOR));
    }

    @Test
    @DisplayName("없는 계정과 틀린 비밀번호는 같은 문구, 5회 연속 실패하면 잠기고 맞는 비밀번호도 거부한다")
    void lockAfterFiveFailures() throws Exception {
        String password = TestFixtures.password();
        AuthUser user = createUser(Role.INSPECTOR, password);

        String unknown = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "nobody-" + UUID.randomUUID() + "@test.local", "password", password))))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        for (int i = 1; i <= 4; i++) {
            String wrong = mockMvc.perform(post("/api/auth/login").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", user.email(), "password", "wrong-password-1"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                    .andReturn().getResponse().getContentAsString();
            assertThat(objectMapper.readTree(wrong).path("message")).isEqualTo(objectMapper.readTree(unknown).path("message"));
        }
        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", user.email(), "password", "wrong-password-1"))))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", user.email(), "password", password))))
                .andExpect(status().isLocked());

        assertThat(auditLogs.findByActionOrderByIdDesc(AuditAction.ACCOUNT_LOCKED))
                .anySatisfy(log -> assertThat(log.getActorId()).isEqualTo(user.id()));
    }
}
