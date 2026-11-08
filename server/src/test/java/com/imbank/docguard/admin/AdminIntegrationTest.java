package com.imbank.docguard.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imbank.docguard.IntegrationTestBase;
import com.imbank.docguard.TestFixtures;
import com.imbank.docguard.analysis.AnalysisJobProcessor;
import com.imbank.docguard.audit.AuditAction;
import com.imbank.docguard.audit.AuditLogRepository;
import com.imbank.docguard.auth.AuthUser;
import com.imbank.docguard.user.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 기능 (F09, F10), 세션 즉시 반영, 시연 정확도 (F12).
 */
class AdminIntegrationTest extends IntegrationTestBase {

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AuditLogRepository auditLogs;

    @Autowired
    AnalysisJobProcessor processor;

    @Autowired
    JdbcTemplate jdbc;

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    @Test
    @DisplayName("관리자는 역할을 바꾸고 이전 값과 새 값이 감사 기록에 남는다. 자기 계정은 바꿀 수 없다")
    void changeRole() throws Exception {
        AuthUser admin = createUser(Role.ADMIN);
        AuthUser target = createUser(Role.INSPECTOR);

        mockMvc.perform(get("/api/admin/users").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + target.id() + ")].email").value(org.hamcrest.Matchers.contains(target.email())))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());

        mockMvc.perform(patch("/api/admin/users/" + target.id()).with(as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("role", "REVIEWER", "reason", "검토 담당 지정"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("REVIEWER"));
        assertThat(auditLogs.findByActionOrderByIdDesc(AuditAction.USER_ROLE_CHANGED)).anySatisfy(log -> {
            assertThat(log.getTargetId()).isEqualTo(target.id().toString());
            assertThat(log.getDetail()).containsEntry("before", "INSPECTOR").containsEntry("after", "REVIEWER");
            assertThat(log.getReason()).isEqualTo("검토 담당 지정");
        });

        mockMvc.perform(patch("/api/admin/users/" + admin.id()).with(as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("role", "INSPECTOR"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELF_CHANGE"));
    }

    @Test
    @DisplayName("비활성화하면 이미 로그인한 세션도 바로 끊기고, 역할을 바꾸면 세션 권한도 바뀐다")
    void sessionReflectsAdminChanges() throws Exception {
        AuthUser admin = createUser(Role.ADMIN);
        String password = TestFixtures.password();
        AuthUser user = createUser(Role.INSPECTOR, password);
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/login").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", user.email(), "password", password))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/reviews/queue").session(session)).andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/admin/users/" + user.id()).with(as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("role", "REVIEWER"))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.role").value("REVIEWER"));
        mockMvc.perform(get("/api/reviews/queue").session(session)).andExpect(status().isOk());

        mockMvc.perform(patch("/api/admin/users/" + user.id()).with(as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "DISABLED"))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", user.email(), "password", password))))
                .andExpect(status().isUnauthorized());

        // 다시 활성화하면 로그인할 수 있습니다.
        mockMvc.perform(patch("/api/admin/users/" + user.id()).with(as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "ACTIVE"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", user.email(), "password", password))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("감사 기록은 행동·사용자로 거르고 최신순으로 나눠 받는다")
    void auditSearch() throws Exception {
        AuthUser admin = createUser(Role.ADMIN);
        AuthUser target = createUser(Role.INSPECTOR);
        mockMvc.perform(patch("/api/admin/users/" + target.id()).with(as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "LOCKED"))))
                .andExpect(status().isOk());

        String body = mockMvc.perform(get("/api/admin/audit-logs").with(as(admin))
                        .param("action", "USER_STATUS_CHANGED").param("actorId", admin.id().toString()).param("size", "5"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode page = objectMapper.readTree(body);
        assertThat(page.path("totalElements").asLong()).isEqualTo(1);
        JsonNode row = page.path("items").get(0);
        assertThat(row.path("action").asText()).isEqualTo("USER_STATUS_CHANGED");
        assertThat(row.path("actorEmail").asText()).isEqualTo(admin.email());
        assertThat(row.path("detail").path("after").asText()).isEqualTo("LOCKED");
    }

    @Test
    @DisplayName("관리자 분석 목록에 파일 이름과 올린 사람이 나온다(BR11)")
    void adminAnalysesShowFileNameAndOwner() throws Exception {
        TestFixtures.requireEval(TestFixtures.API_RAW_DIR);
        AuthUser admin = createUser(Role.ADMIN);
        AuthUser inspector = createUser(Role.INSPECTOR);
        mockMvc.perform(multipart("/api/analyses")
                        .file(new MockMultipartFile("file", "고객이름_set02.pdf", "application/pdf", TestFixtures.pdf(1)))
                        .with(as(inspector)).with(csrfHeader()))
                .andExpect(status().isAccepted());

        String body = mockMvc.perform(get("/api/admin/analyses").with(as(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("고객이름_set02.pdf").contains(inspector.email());
    }

    @Test
    @DisplayName("시연 정확도: 재생 모드 결과는 빼고, 실제 실행 결과를 정답표와 비교한다")
    void accuracy() throws Exception {
        TestFixtures.requireEval(TestFixtures.ANSWER_KEY);
        TestFixtures.requireEval(TestFixtures.API_RAW_DIR);
        AuthUser inspector = createUser(Role.INSPECTOR);
        String created = mockMvc.perform(multipart("/api/analyses")
                        .file(new MockMultipartFile("file", "금융보안원_미션2_set02.pdf", "application/pdf", TestFixtures.pdf(1)))
                        .with(as(inspector)).with(csrfHeader()))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(created).path("id").asText());
        processor.processAll();
        processor.processAll();

        JsonNode replayOnly = objectMapper.readTree(mockMvc.perform(get("/api/demo/accuracy").with(as(inspector)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(replayOnly.path("available").asBoolean()).isTrue();
        assertThat(replayOnly.path("total").asInt()).isEqualTo(10);
        assertThat(replayOnly.path("executed").asInt()).isZero();

        // 실제 실행처럼 보이도록 Agent ID 를 바꿉니다(재생 모드 표시 제거).
        jdbc.update("UPDATE analyses SET studio_agent_id = 'agt_test' WHERE id = ?", id);
        JsonNode report = objectMapper.readTree(mockMvc.perform(get("/api/demo/accuracy").with(as(inspector)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(report.path("executed").asInt()).isEqualTo(1);
        assertThat(report.path("matched").asInt()).isEqualTo(1);
        JsonNode set02 = null;
        for (JsonNode r : report.path("rows")) {
            if ("set02".equals(r.path("tag").asText())) {
                set02 = r;
            }
        }
        assertThat(set02).isNotNull();
        assertThat(set02.path("actualGrade").asText()).isEqualTo("상");
        assertThat(set02.path("match").asBoolean()).isTrue();
    }
}
