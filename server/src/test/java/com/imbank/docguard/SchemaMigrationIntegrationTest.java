package com.imbank.docguard;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M0 통합 테스트: 실제 PostgreSQL 16(Testcontainers)에 Flyway V1 이 적용되고,
 * JPA validate(엔티티와 스키마 일치)로 애플리케이션이 시작되며, actuator 는 health 만 공개되는지 확인합니다. (DATABASE.md 6장, SECURITY.md 3장)
 * Docker 가 꺼져 있으면 건너뜁니다(SKIPPED). 그때는 Docker Desktop 을 먼저 켭니다.
 */
class SchemaMigrationIntegrationTest extends IntegrationTestBase {

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("Flyway 마이그레이션이 모두 적용되고 DATABASE.md 의 테이블 6개가 만들어진다")
    void flywayAppliesV1() {
        MigrationInfo current = flyway.info().current();
        assertThat(current).isNotNull();
        assertThat(current.getVersion().getVersion()).isEqualTo("2");
        assertThat(current.getState().isApplied()).isTrue();

        List<String> tables = jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE' AND table_name <> 'flyway_schema_history'
                ORDER BY table_name
                """, String.class);
        assertThat(tables).containsExactly(
                "analyses", "analysis_results", "audit_logs", "findings", "reviews", "users");
    }

    @Test
    @DisplayName("원문값을 담는 평문 컬럼이 없고, 결과는 bytea 암호화 컬럼에만 둔다")
    void noPlaintextRawValueColumns() {
        String payloadType = jdbc.queryForObject("""
                SELECT data_type FROM information_schema.columns
                WHERE table_name = 'analysis_results' AND column_name = 'encrypted_payload'
                """, String.class);
        assertThat(payloadType).isEqualTo("bytea");

        List<String> findingColumns = jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns WHERE table_name = 'findings'
                """, String.class);
        assertThat(findingColumns).doesNotContain("raw_value", "evidence", "original_value");
    }

    @Test
    @DisplayName("CHECK 제약: 정의되지 않은 역할은 저장할 수 없다")
    void roleCheckConstraint() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO users (email, display_name, password_hash, role)
                VALUES ('check-test@example.com', '시험', 'x', 'SUPERUSER')
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("/actuator/health 는 로그인 없이 UP 을 돌려준다")
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    @DisplayName("health 외의 actuator 와 /api 는 로그인 없이 접근할 수 없다")
    void otherEndpointsAreClosed() throws Exception {
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/analyses")).andExpect(status().isUnauthorized());
    }
}
