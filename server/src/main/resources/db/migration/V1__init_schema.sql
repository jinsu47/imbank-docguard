-- V1: 초기 스키마 (DATABASE.md 2~5장)
-- 원문값, 원문 근거, 보고서 본문은 평문 컬럼에 두지 않고 analysis_results.encrypted_payload 에만 암호화해 둡니다.
-- 이미 적용한 이 파일은 수정하지 않습니다. 바꿀 내용은 V2 이후 새 파일로 추가합니다.
-- 계정 시드는 넣지 않습니다(비밀번호가 저장소에 남기 때문). scripts/seed-accounts 가 만듭니다.

-- ---------------------------------------------------------------------------
-- users: 사용자와 역할
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id                 BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email              VARCHAR(254) NOT NULL,                -- 개인정보. 소문자로 저장
    display_name       VARCHAR(50)  NOT NULL,                -- 개인정보
    password_hash      VARCHAR(100) NOT NULL,                -- BCrypt(cost 12)
    role               VARCHAR(20)  NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    failed_login_count INT          NOT NULL DEFAULT 0,
    locked_until       TIMESTAMPTZ,
    last_login_at      TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_email_lower CHECK (email = lower(email)),
    CONSTRAINT ck_users_role CHECK (role IN ('INSPECTOR', 'REVIEWER', 'ADMIN')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED')),
    CONSTRAINT ck_users_failed_login_count CHECK (failed_login_count >= 0)
);

-- ---------------------------------------------------------------------------
-- analyses: 문서 한 건의 분석 (요약은 보관 기간이 지나도 남음)
-- ---------------------------------------------------------------------------
CREATE TABLE analyses (
    id                       UUID         PRIMARY KEY,       -- URL 에 쓰므로 추측하기 어려운 값
    owner_id                 BIGINT       NOT NULL,
    status                   VARCHAR(20)  NOT NULL,
    original_filename_enc    VARCHAR(512) NOT NULL,          -- 개인정보(AES-256-GCM 암호화)
    stored_path              VARCHAR(255),                   -- 저장소 안의 무작위 파일 이름. 삭제 후 NULL
    file_sha256              CHAR(64)     NOT NULL,
    file_size                BIGINT       NOT NULL,
    content_type             VARCHAR(50)  NOT NULL,
    page_count               INT,
    studio_agent_id          VARCHAR(64)  NOT NULL,
    studio_config_id         VARCHAR(64)  NOT NULL,
    studio_file_id           VARCHAR(64),
    studio_job_id            VARCHAR(64),
    document_type            VARCHAR(50),
    document_type_confidence NUMERIC(4, 3),
    risk_grade               VARCHAR(10),
    grade_provisional        BOOLEAN      NOT NULL DEFAULT FALSE,
    review_required          BOOLEAN,
    review_status            VARCHAR(20)  NOT NULL DEFAULT 'NOT_REQUIRED',
    parse_check_verdict      VARCHAR(10),
    extract_check_verdict    VARCHAR(10),
    finding_count            INT,
    completed_steps          JSONB        NOT NULL DEFAULT '[]'::jsonb,
    error_code               VARCHAR(40),                    -- 오류 메시지 원문은 저장하지 않음
    submitted_at             TIMESTAMPTZ,
    completed_at             TIMESTAMPTZ,
    expires_at               TIMESTAMPTZ  NOT NULL,          -- created_at + APP_RETENTION_HOURS
    purged_at                TIMESTAMPTZ,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_analyses_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_analyses_status CHECK (status IN ('UPLOADING', 'QUEUED', 'RUNNING', 'COMPLETED', 'FAILED', 'PURGED')),
    CONSTRAINT ck_analyses_content_type CHECK (content_type IN ('application/pdf', 'image/jpeg', 'image/png')),
    CONSTRAINT ck_analyses_file_size CHECK (file_size >= 0),
    CONSTRAINT ck_analyses_risk_grade CHECK (risk_grade IN ('HIGH', 'MEDIUM', 'LOW', 'NONE', 'UNKNOWN')),
    CONSTRAINT ck_analyses_review_status CHECK (review_status IN ('NOT_REQUIRED', 'PENDING', 'CONFIRMED', 'DISPUTED')),
    CONSTRAINT ck_analyses_parse_check_verdict CHECK (parse_check_verdict IN ('green', 'yellow', 'red')),
    CONSTRAINT ck_analyses_extract_check_verdict CHECK (extract_check_verdict IN ('green', 'yellow', 'red')),
    CONSTRAINT ck_analyses_completed_steps_array CHECK (jsonb_typeof(completed_steps) = 'array')
);

-- 내 분석 목록
CREATE INDEX ix_analyses_owner_created ON analyses (owner_id, created_at DESC);
-- 검토 대기열
CREATE INDEX ix_analyses_review_pending ON analyses (review_status, created_at) WHERE review_status = 'PENDING';
-- 상태 조회 스케줄러
CREATE INDEX ix_analyses_in_progress ON analyses (status) WHERE status IN ('UPLOADING', 'QUEUED', 'RUNNING');
-- 보관 기간 정리
CREATE INDEX ix_analyses_expires ON analyses (expires_at) WHERE purged_at IS NULL;

-- ---------------------------------------------------------------------------
-- analysis_results: 보고서·원문값·인용을 묶은 암호화 결과 (분석 하나에 하나)
-- ---------------------------------------------------------------------------
CREATE TABLE analysis_results (
    analysis_id       UUID        PRIMARY KEY,
    encrypted_payload BYTEA       NOT NULL,                  -- 개인정보(AES-256-GCM: 12바이트 IV + 암호문 + 태그)
    key_version       SMALLINT    NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_analysis_results_analysis FOREIGN KEY (analysis_id) REFERENCES analyses (id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------------
-- findings: 발견 항목 (원문값 없이 목록·상자 표시에 필요한 값만)
-- ---------------------------------------------------------------------------
CREATE TABLE findings (
    id             BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    analysis_id    UUID         NOT NULL,
    row_index      INT          NOT NULL,                    -- encrypted_payload.findings[].rowIndex 와 연결
    item_label     VARCHAR(100) NOT NULL,
    info_type      VARCHAR(30)  NOT NULL,
    subject_role   VARCHAR(30)  NOT NULL,
    mask_state     VARCHAR(20)  NOT NULL,
    value_nature   VARCHAR(20),
    policy_linked  BOOLEAN      NOT NULL,
    page           INT,
    word_boxes     JSONB,                                    -- 값 자체의 상자들(상대 좌표 0~1)
    element_box    JSONB,                                    -- 값이 들어 있는 요소의 상자
    confidence     VARCHAR(10),
    CONSTRAINT fk_findings_analysis FOREIGN KEY (analysis_id) REFERENCES analyses (id) ON DELETE CASCADE,
    -- 결과 조회 인덱스를 겸합니다.
    CONSTRAINT uk_findings_analysis_row UNIQUE (analysis_id, row_index),
    CONSTRAINT ck_findings_row_index CHECK (row_index >= 0)
);

-- ---------------------------------------------------------------------------
-- reviews: 검토 결정 (분석 하나에 최대 하나, 변경 없음)
-- ---------------------------------------------------------------------------
CREATE TABLE reviews (
    id          BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    analysis_id UUID        NOT NULL,
    reviewer_id BIGINT      NOT NULL,
    decision    VARCHAR(20) NOT NULL,
    comment     TEXT,                                        -- 원문값을 적지 말라고 화면에서 안내
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_reviews_analysis FOREIGN KEY (analysis_id) REFERENCES analyses (id) ON DELETE CASCADE,
    CONSTRAINT fk_reviews_reviewer FOREIGN KEY (reviewer_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT uk_reviews_analysis UNIQUE (analysis_id),
    CONSTRAINT ck_reviews_decision CHECK (decision IN ('CONFIRMED', 'DISPUTED')),
    CONSTRAINT ck_reviews_comment_length CHECK (comment IS NULL OR char_length(comment) <= 1000),
    -- BR14: 이의는 사유가 필수입니다. 애플리케이션 검증과 함께 DB 에서도 막습니다.
    CONSTRAINT ck_reviews_disputed_comment CHECK (decision <> 'DISPUTED' OR (comment IS NOT NULL AND btrim(comment) <> ''))
);

-- ---------------------------------------------------------------------------
-- audit_logs: 감사 기록 (추가만 하고 수정·삭제하지 않음)
-- ---------------------------------------------------------------------------
CREATE TABLE audit_logs (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_id    BIGINT,                                      -- 로그인 실패 등에서는 NULL
    action      VARCHAR(40)  NOT NULL,
    target_type VARCHAR(30),
    target_id   VARCHAR(64),
    reason      VARCHAR(300),
    ip_address  VARCHAR(45),                                 -- 개인정보
    user_agent  VARCHAR(300),
    request_id  VARCHAR(36),
    detail      JSONB,                                       -- 원문값을 넣지 않음
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_audit_logs_actor FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX ix_audit_logs_created ON audit_logs (created_at DESC);
CREATE INDEX ix_audit_logs_actor_created ON audit_logs (actor_id, created_at DESC);
