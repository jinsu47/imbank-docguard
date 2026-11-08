package com.imbank.docguard.auth;

import com.imbank.docguard.IntegrationTestBase;
import com.imbank.docguard.TestFixtures;
import com.imbank.docguard.user.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 역할 × API 권한 표 (SECURITY.md 1장, 3장). 비로그인(ANON) 포함 4개 역할을 모두 확인합니다.
 * 없는 분석 ID 는 접근할 수 있는 역할에도 404 이므로, 경로 규칙이 막는지(401·403) 통과하는지(404 등)를 구분합니다.
 */
class AuthorizationMatrixTest extends IntegrationTestBase {

    @ParameterizedTest(name = "{0} {1}: ANON={2} INSPECTOR={3} REVIEWER={4} ADMIN={5}")
    @CsvSource({
            "GET,    /api/auth/me,                   401, 200, 200, 200",
            "GET,    /api/analyses,                  401, 200, 200, 200",
            "POST,   /api/analyses,                  401, 400, 400, 400",
            "GET,    /api/analyses/{id},             401, 404, 404, 404",
            "GET,    /api/analyses/{id}/result,      401, 404, 404, 404",
            "GET,    /api/analyses/{id}/file,        401, 404, 404, 404",
            "POST,   /api/analyses/{id}/reveal,      401, 404, 404, 404",
            "POST,   /api/analyses/{id}/retry,       401, 404, 404, 404",
            "DELETE, /api/analyses/{id},             401, 404, 404, 404",
            "POST,   /api/analyses/{id}/review,      401, 403, 404, 404",
            "GET,    /api/reviews/queue,             401, 403, 200, 200",
            "GET,    /api/admin/users,               401, 403, 403, 200",
            "PATCH,  /api/admin/users/999999,        401, 403, 403, 404",
            "GET,    /api/admin/audit-logs,          401, 403, 403, 200",
            "GET,    /api/admin/analyses,            401, 403, 403, 200",
            "GET,    /api/demo/accuracy,             401, 200, 200, 200",
            "GET,    /actuator/env,                  401, 403, 403, 403",
            "GET,    /actuator/health,               200, 200, 200, 200",
    })
    @DisplayName("AuthorizationMatrixTest: 역할별 응답 코드")
    void matrix(String method, String path, int anon, int inspector, int reviewer, int admin) throws Exception {
        String resolved = path.replace("{id}", UUID.randomUUID().toString());
        assertThat(status(method, resolved, null)).as("ANON").isEqualTo(anon);
        assertThat(status(method, resolved, Role.INSPECTOR)).as("INSPECTOR").isEqualTo(inspector);
        assertThat(status(method, resolved, Role.REVIEWER)).as("REVIEWER").isEqualTo(reviewer);
        assertThat(status(method, resolved, Role.ADMIN)).as("ADMIN").isEqualTo(admin);
    }

    private int status(String method, String path, Role role) throws Exception {
        MockHttpServletRequestBuilder request = switch (method) {
            case "GET" -> get(path);
            case "DELETE" -> delete(path);
            case "PATCH" -> patch(path).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}");
            case "POST" -> path.equals("/api/analyses")
                    // 빈 파일이라 통과한 역할은 400 을 받습니다(권한 규칙은 통과).
                    ? multipart(path).file(new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[0]))
                    : post(path).contentType(MediaType.APPLICATION_JSON).content(body(path));
            default -> throw new IllegalArgumentException(method);
        };
        request.with(csrf());
        if (role != null) {
            request.with(as(createUser(role, TestFixtures.password())));
        }
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }

    private static String body(String path) {
        if (path.endsWith("/reveal")) {
            return "{\"reason\":\"권한 시험\"}";
        }
        if (path.endsWith("/review")) {
            return "{\"decision\":\"CONFIRMED\"}";
        }
        return "{}";
    }
}
