package com.imbank.docguard.studio;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.security.SecureRandom;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Studio 호출 순서와 헤더 (ARCHITECTURE.md 4장). 가짜 서버로만 시험하고 실제 Upstage API 는 부르지 않습니다.
 */
class StudioHttpClientTest {

    private static final String BASE = "https://studio.test/v2";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockRestServiceServer server;
    private StudioHttpClient client;
    private String apiKey;

    @BeforeEach
    void setUp() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        apiKey = "test-" + HexFormat.of().formatHex(bytes);
        StudioProperties properties = new StudioProperties(StudioProperties.Mode.LIVE, apiKey, "proj_test", "agt_test",
                "2", BASE, 3000, 600, null, 0, StudioResultMapperTest.STEPS);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new StudioHttpClient(properties, builder, objectMapper);
    }

    @Test
    @DisplayName("업로드: multipart, purpose=user_data, Bearer 키와 x-project-id, 원래 파일 이름을 보내지 않음")
    void upload() {
        server.expect(requestTo(BASE + "/files"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + apiKey))
                .andExpect(header("x-project-id", "proj_test"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("user_data")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("filename=\"document.pdf\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("홍길동"))))
                .andRespond(withSuccess("{\"id\":\"file_abc\"}", MediaType.APPLICATION_JSON));

        String id = client.uploadFile("%PDF-1.4 test".getBytes(), "application/pdf", "홍길동_대출신청서.pdf");

        assertThat(id).isEqualTo("file_abc");
        server.verify();
    }

    @Test
    @DisplayName("작업 생성: model=Agent ID, config_id 고정, include=[last], input_file")
    void createJob() {
        server.expect(requestTo(BASE + "/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-project-id", "proj_test"))
                .andExpect(jsonPath("$.model").value("agt_test"))
                .andExpect(jsonPath("$.config_id").value("2"))
                .andExpect(jsonPath("$.include[0]").value("last"))
                .andExpect(jsonPath("$.input[0].role").value("user"))
                .andExpect(jsonPath("$.input[0].content[0].type").value("input_file"))
                .andExpect(jsonPath("$.input[0].content[0].file_id").value("file_abc"))
                .andRespond(withSuccess("{\"id\":\"job_1\",\"status\":\"queued\"}", MediaType.APPLICATION_JSON));

        assertThat(client.createJob("file_abc")).isEqualTo("job_1");
        server.verify();
    }

    @Test
    @DisplayName("작업 조회: include[]=all")
    void getJob() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/responses/job_1?include")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("x-project-id", "proj_test"))
                .andRespond(withSuccess("{\"id\":\"job_1\",\"status\":\"in_progress\",\"output\":[]}", MediaType.APPLICATION_JSON));

        assertThat(client.getJob("job_1").path("status").asText()).isEqualTo("in_progress");
        server.verify();
    }

    @Test
    @DisplayName("429 와 5xx 는 다시 시도할 수 있는 실패, 403 은 아님. 오류 메시지에 응답 본문을 넣지 않음")
    void errors() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/responses/job_1?include")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("{\"error\":{\"code\":\"rate_limit\",\"message\":\"secret detail\"}}"));
        assertThatThrownBy(() -> client.getJob("job_1"))
                .isInstanceOfSatisfying(StudioException.class, e -> {
                    assertThat(e.retryable()).isTrue();
                    assertThat(e.code()).isEqualTo("STUDIO_HTTP_429");
                    assertThat(e.getMessage()).doesNotContain("secret detail");
                });

        server.reset();
        server.expect(requestTo(BASE + "/files"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).body("{\"error\":{\"message\":\"No access to file\"}}"));
        assertThatThrownBy(() -> client.uploadFile(new byte[]{1}, "image/png", null))
                .isInstanceOfSatisfying(StudioException.class, e -> assertThat(e.retryable()).isFalse());
    }

    @Test
    @DisplayName("키가 비어 있으면 요청을 보내지 않고 STUDIO_NOT_CONFIGURED")
    void notConfigured() {
        StudioProperties empty = new StudioProperties(StudioProperties.Mode.LIVE, "", "", "", "2", BASE, 3000, 600,
                null, 0, StudioResultMapperTest.STEPS);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer noCalls = MockRestServiceServer.bindTo(builder).build();
        StudioHttpClient unconfigured = new StudioHttpClient(empty, builder, objectMapper);

        assertThatThrownBy(() -> unconfigured.uploadFile(new byte[]{1}, "application/pdf", null))
                .isInstanceOfSatisfying(StudioException.class, e -> assertThat(e.code()).isEqualTo("STUDIO_NOT_CONFIGURED"));
        noCalls.verify();
    }
}
