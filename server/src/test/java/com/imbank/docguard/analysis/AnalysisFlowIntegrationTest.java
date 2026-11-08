package com.imbank.docguard.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imbank.docguard.IntegrationTestBase;
import com.imbank.docguard.TestFixtures;
import com.imbank.docguard.audit.AuditAction;
import com.imbank.docguard.audit.AuditLogRepository;
import com.imbank.docguard.auth.AuthUser;
import com.imbank.docguard.studio.StudioModels;
import com.imbank.docguard.studio.StudioResultMapper;
import com.imbank.docguard.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 업로드 → Studio 작업(재생 모드) → 결과 저장 → 결과·원본·원문 보기 → 검토 → 삭제 (F02~F08).
 * 재생 모드는 2026-11-06 에 실제 API 로 받은 set02 응답을 돌려줍니다.
 */
class AnalysisFlowIntegrationTest extends IntegrationTestBase {

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AnalysisJobProcessor processor;

    @Autowired
    AuditLogRepository auditLogs;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StudioResultMapper mapper;

    @BeforeEach
    void requireReplayData() {
        TestFixtures.requireEval(TestFixtures.API_RAW_DIR);
    }

    private UUID upload(AuthUser user, String filename, byte[] content) throws Exception {
        String body = mockMvc.perform(multipart("/api/analyses")
                        .file(new MockMultipartFile("file", filename, "application/pdf", content))
                        .with(as(user)).with(csrf()))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).path("id").asText());
    }

    private JsonNode detail(AuthUser user, UUID id) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/analyses/" + id).with(as(user)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private UUID uploadAndComplete(AuthUser user) throws Exception {
        UUID id = upload(user, "금융보안원_미션2_set02.pdf", TestFixtures.pdf(1));
        processor.processAll();
        processor.processAll();
        assertThat(detail(user, id).path("summary").path("status").asText()).isEqualTo("COMPLETED");
        return id;
    }

    @Test
    @DisplayName("업로드부터 결과까지: 등급·발견 항목·인용을 저장하고, 결과 응답에는 원문값이 없다")
    void uploadToResult() throws Exception {
        AuthUser inspector = createUser(Role.INSPECTOR);
        byte[] pdf = TestFixtures.pdf(1);
        UUID id = upload(inspector, "금융보안원_미션2_set02.pdf", pdf);

        JsonNode created = detail(inspector, id);
        assertThat(created.path("summary").path("status").asText()).isEqualTo("UPLOADING");
        assertThat(created.path("summary").path("fileName").asText()).isEqualTo("금융보안원_미션2_set02.pdf");

        processor.processAll();
        assertThat(detail(inspector, id).path("summary").path("status").asText()).isEqualTo("QUEUED");
        processor.processAll();

        JsonNode done = detail(inspector, id);
        JsonNode summary = done.path("summary");
        assertThat(summary.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(summary.path("riskGrade").asText()).isEqualTo("HIGH");
        assertThat(summary.path("reviewStatus").asText()).isEqualTo("NOT_REQUIRED");
        assertThat(summary.path("replay").asBoolean()).isTrue();
        assertThat(summary.path("documentType").asText()).isEqualTo("예금");
        assertThat(done.path("completedSteps")).hasSize(7);
        assertThat(done.path("owner").asBoolean()).isTrue();

        String resultBody = mockMvc.perform(get("/api/analyses/" + id + "/result").with(as(inspector)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn().getResponse().getContentAsString();
        JsonNode result = objectMapper.readTree(resultBody);
        assertThat(result.path("findings").size()).isEqualTo(summary.path("findingCount").asInt()).isPositive();
        assertThat(result.path("sections").get(0).path("title").asText()).isEqualTo("요약");
        assertThat(result.path("citations").size()).isPositive();
        assertThat(result.path("findings").get(0).path("wordBoxes").size()).isPositive();

        // BR08: 결과 응답에는 표시값만 있고, 가리지 않은 원문값은 없어야 합니다.
        List<String> rawValues = rawValues("set02");
        assertThat(rawValues).isNotEmpty();
        for (String raw : rawValues) {
            assertThat(resultBody).as("결과 응답에 원문값이 들어 있음").doesNotContain(raw);
        }
    }

    private List<String> rawValues(String tag) throws Exception {
        JsonNode raw = objectMapper.readTree(TestFixtures.rawResponse(tag).toFile());
        StudioModels.Outcome outcome = mapper.map(raw).outcome();
        return outcome.findings().stream()
                .filter(f -> "미마스킹".equals(f.maskState()))
                .map(StudioModels.ExtractedFinding::rawValue)
                .filter(v -> v.length() >= 3)
                .toList();
    }

    @Test
    @DisplayName("원본 파일은 소유자만 받고 처음 열 때 감사 기록, 원문 보기는 사유 필수이며 감사 기록을 남긴다")
    void fileAndReveal() throws Exception {
        AuthUser inspector = createUser(Role.INSPECTOR);
        UUID id = uploadAndComplete(inspector);

        byte[] file = mockMvc.perform(get("/api/analyses/" + id + "/file").with(as(inspector)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(file, 0, 5)).isEqualTo("%PDF-");
        assertThat(auditLogs.findByActionOrderByIdDesc(AuditAction.ORIGINAL_FILE_VIEWED))
                .anySatisfy(a -> assertThat(a.getTargetId()).isEqualTo(id.toString()));

        mockMvc.perform(post("/api/analyses/" + id + "/reveal").with(as(inspector)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
        String revealed = mockMvc.perform(post("/api/analyses/" + id + "/reveal").with(as(inspector)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"가림 범위 확인\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(revealed).contains(rawValues("set02").getFirst());
        assertThat(auditLogs.findByActionOrderByIdDesc(AuditAction.RAW_VALUE_REVEALED))
                .anySatisfy(a -> {
                    assertThat(a.getTargetId()).isEqualTo(id.toString());
                    assertThat(a.getReason()).isEqualTo("가림 범위 확인");
                    assertThat(a.getActorId()).isEqualTo(inspector.id());
                });
    }

    @Test
    @DisplayName("다른 사람의 분석은 404 이고, 관리자는 모든 분석을 볼 수 있다(BR11)")
    void accessIsolation() throws Exception {
        AuthUser owner = createUser(Role.INSPECTOR);
        AuthUser other = createUser(Role.INSPECTOR);
        AuthUser reviewer = createUser(Role.REVIEWER);
        AuthUser admin = createUser(Role.ADMIN);
        UUID id = uploadAndComplete(owner);

        for (String path : List.of("", "/result", "/file")) {
            mockMvc.perform(get("/api/analyses/" + id + path).with(as(other))).andExpect(status().isNotFound());
            // 사람 검토가 필요 없는 분석은 검토자의 검토 범위가 아닙니다.
            mockMvc.perform(get("/api/analyses/" + id + path).with(as(reviewer))).andExpect(status().isNotFound());
            mockMvc.perform(get("/api/analyses/" + id + path).with(as(admin))).andExpect(status().isOk());
        }
        mockMvc.perform(delete("/api/analyses/" + id).with(as(other)).with(csrf())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/analyses").with(as(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + id + "')]").isEmpty());
    }

    @Test
    @DisplayName("검토: 대기열 → 이의는 사유 필수 → 확인 완료 → 다시 기록 불가, 자기 분석은 검토 불가")
    void reviewFlow() throws Exception {
        AuthUser owner = createUser(Role.INSPECTOR);
        AuthUser reviewer = createUser(Role.REVIEWER);
        UUID id = uploadAndComplete(owner);
        // 저장 응답(set02)은 사람 검토가 필요 없으므로, 검토 대기 상태를 DB 에서 만듭니다.
        jdbc.update("UPDATE analyses SET review_required = true, review_status = 'PENDING' WHERE id = ?", id);

        mockMvc.perform(get("/api/reviews/queue").with(as(owner))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reviews/queue").with(as(reviewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + id + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].fileName").isEmpty());

        JsonNode d = detail(reviewer, id);
        assertThat(d.path("canReview").asBoolean()).isTrue();
        assertThat(d.path("owner").asBoolean()).isFalse();
        mockMvc.perform(get("/api/analyses/" + id + "/result").with(as(reviewer))).andExpect(status().isOk());
        mockMvc.perform(post("/api/analyses/" + id + "/review").with(as(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"CONFIRMED\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/analyses/" + id + "/review").with(as(reviewer)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"DISPUTED\",\"comment\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMENT_REQUIRED"));
        mockMvc.perform(post("/api/analyses/" + id + "/review").with(as(reviewer)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(
                                Map.of("decision", "CONFIRMED", "comment", "판정 유지"))))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/analyses/" + id + "/review").with(as(reviewer)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"CONFIRMED\"}"))
                .andExpect(status().isConflict());

        JsonNode after = detail(reviewer, id);
        assertThat(after.path("summary").path("reviewStatus").asText()).isEqualTo("CONFIRMED");
        assertThat(after.path("review").path("decision").asText()).isEqualTo("CONFIRMED");
        assertThat(after.path("canReview").asBoolean()).isFalse();
        // BR01: 검토는 Studio 판정(등급)을 바꾸지 않습니다.
        assertThat(after.path("summary").path("riskGrade").asText()).isEqualTo("HIGH");
        mockMvc.perform(get("/api/reviews/queue").with(as(reviewer)))
                .andExpect(jsonPath("$[?(@.id == '" + id + "')]").isEmpty());

        // BR13: 검토자가 올린 분석은 자기 대기열에 나오지 않고 검토할 수 없습니다.
        UUID own = uploadAndComplete(reviewer);
        jdbc.update("UPDATE analyses SET review_required = true, review_status = 'PENDING' WHERE id = ?", own);
        mockMvc.perform(get("/api/reviews/queue").with(as(reviewer)))
                .andExpect(jsonPath("$[?(@.id == '" + own + "')]").isEmpty());
        mockMvc.perform(post("/api/analyses/" + own + "/review").with(as(reviewer)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"CONFIRMED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("OWN_ANALYSIS"));
    }

    @Test
    @DisplayName("관리자는 다른 사람의 분석을 원문 보기·검토·삭제할 수 있고 모두 감사 기록에 남는다(BR11)")
    void adminManagesAnyAnalysis() throws Exception {
        AuthUser owner = createUser(Role.INSPECTOR);
        AuthUser admin = createUser(Role.ADMIN);
        UUID id = uploadAndComplete(owner);
        jdbc.update("UPDATE analyses SET review_required = true, review_status = 'PENDING' WHERE id = ?", id);

        JsonNode d = detail(admin, id);
        assertThat(d.path("owner").asBoolean()).isFalse();
        assertThat(d.path("canManage").asBoolean()).isTrue();
        assertThat(d.path("canReview").asBoolean()).isTrue();
        assertThat(d.path("summary").path("fileName").asText()).isEqualTo("금융보안원_미션2_set02.pdf");

        mockMvc.perform(get("/api/reviews/queue").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + id + "')]").isNotEmpty());
        mockMvc.perform(post("/api/analyses/" + id + "/reveal").with(as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"관리자 확인\"}"))
                .andExpect(status().isOk());
        assertThat(auditLogs.findByActionOrderByIdDesc(AuditAction.RAW_VALUE_REVEALED))
                .anySatisfy(a -> {
                    assertThat(a.getTargetId()).isEqualTo(id.toString());
                    assertThat(a.getActorId()).isEqualTo(admin.id());
                });

        mockMvc.perform(post("/api/analyses/" + id + "/review").with(as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"CONFIRMED\"}"))
                .andExpect(status().isNoContent());
        assertThat(detail(admin, id).path("summary").path("reviewStatus").asText()).isEqualTo("CONFIRMED");

        mockMvc.perform(delete("/api/analyses/" + id).with(as(admin)).with(csrf())).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/analyses/" + id).with(as(owner))).andExpect(status().isNotFound());
        assertThat(auditLogs.findByActionOrderByIdDesc(AuditAction.ANALYSIS_DELETED))
                .anySatisfy(a -> {
                    assertThat(a.getTargetId()).isEqualTo(id.toString());
                    assertThat(a.getActorId()).isEqualTo(admin.id());
                    assertThat(a.getDetail()).containsEntry("ownerId", owner.id().intValue());
                });
    }

    @Test
    @DisplayName("관리자도 자기가 올린 분석은 검토할 수 없다(BR13)")
    void adminCannotReviewOwn() throws Exception {
        AuthUser admin = createUser(Role.ADMIN);
        UUID own = uploadAndComplete(admin);
        jdbc.update("UPDATE analyses SET review_required = true, review_status = 'PENDING' WHERE id = ?", own);

        mockMvc.perform(get("/api/reviews/queue").with(as(admin)))
                .andExpect(jsonPath("$[?(@.id == '" + own + "')]").isEmpty());
        mockMvc.perform(post("/api/analyses/" + own + "/review").with(as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"CONFIRMED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("OWN_ANALYSIS"));
    }

    @Test
    @DisplayName("업로드 검증: 형식은 시그니처로 확인하고, 진행 중 건수는 제한하지 않는다(BR06)")
    void uploadValidation() throws Exception {
        AuthUser inspector = createUser(Role.INSPECTOR);
        mockMvc.perform(multipart("/api/analyses")
                        .file(new MockMultipartFile("file", "fake.pdf", "application/pdf", "hello".getBytes()))
                        .with(as(inspector)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE"));

        for (int i = 0; i < 5; i++) {
            upload(inspector, "doc" + i + ".pdf", TestFixtures.pdf(1));
        }
    }

    @Test
    @DisplayName("업로드 검증(BR05): 파일 크기와 쪽수는 제한하지 않는다")
    void noSizeOrPageLimit() throws Exception {
        AuthUser inspector = createUser(Role.INSPECTOR);
        UUID longPdf = upload(inspector, "long.pdf", TestFixtures.pdf(21));
        assertThat(jdbc.queryForObject("SELECT page_count FROM analyses WHERE id = ?", Integer.class, longPdf))
                .isEqualTo(21);

        byte[] largeImage = new byte[25 * 1024 * 1024];
        largeImage[0] = (byte) 0xFF;
        largeImage[1] = (byte) 0xD8;
        largeImage[2] = (byte) 0xFF;
        UUID large = upload(inspector, "large.jpg", largeImage);
        assertThat(jdbc.queryForObject("SELECT file_size FROM analyses WHERE id = ?", Long.class, large))
                .isEqualTo(largeImage.length);
    }

    @Test
    @DisplayName("제한 시간은 쪽수에 비례해 늘어나, 긴 문서는 같은 시간이 지나도 실패하지 않는다")
    void timeoutGrowsWithPages() throws Exception {
        AuthUser inspector = createUser(Role.INSPECTOR);
        UUID shortDoc = upload(inspector, "금융보안원_미션2_set02.pdf", TestFixtures.pdf(1));
        UUID longDoc = upload(inspector, "금융보안원_미션2_set02.pdf", TestFixtures.pdf(21));
        processor.processAll();
        // 기본 제한 600초는 넘었지만, 21쪽 문서의 제한(21 × 60 = 1,260초)은 넘지 않은 상태입니다.
        jdbc.update("UPDATE analyses SET submitted_at = now() - interval '700 seconds' WHERE id IN (?, ?)", shortDoc, longDoc);
        processor.processAll();

        assertThat(detail(inspector, shortDoc).path("summary").path("status").asText()).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT error_code FROM analyses WHERE id = ?", String.class, shortDoc))
                .isEqualTo("STUDIO_TIMEOUT");
        assertThat(detail(inspector, longDoc).path("summary").path("status").asText()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("소유자 삭제: 원본·결과·발견 항목을 지우고 감사 기록을 남긴다")
    void deleteByOwner() throws Exception {
        AuthUser inspector = createUser(Role.INSPECTOR);
        UUID id = uploadAndComplete(inspector);

        mockMvc.perform(delete("/api/analyses/" + id).with(as(inspector)).with(csrf())).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/analyses/" + id).with(as(inspector))).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM findings WHERE analysis_id = ?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM analysis_results WHERE analysis_id = ?", Integer.class, id)).isZero();
        assertThat(auditLogs.findByActionOrderByIdDesc(AuditAction.ANALYSIS_DELETED))
                .anySatisfy(a -> assertThat(a.getTargetId()).isEqualTo(id.toString()));
    }
}
