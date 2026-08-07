-- WWA Agent Workspace Hub — Oracle current schema
-- Generated for greenfield UAT / internal environment setup.
-- Use this script for a fresh Oracle schema.
-- Do not run V2-V21 incremental scripts on top of this file for a brand-new database,
-- because this script already includes the current end-state columns from V2 through V21.

-- ═══════════════════════════════════════════════════════════════════════════
-- 1. DA_RELEASE_FLOW — root aggregate
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE DA_RELEASE_FLOW (
    id                    VARCHAR2(36)   NOT NULL,
    project_id            VARCHAR2(255)  NOT NULL,
    project_name          VARCHAR2(255)  NOT NULL,
    release_id            VARCHAR2(255),
    normalized_release_id VARCHAR2(255)  NOT NULL,
    current_stage         VARCHAR2(10)   NOT NULL,
    flow_status           VARCHAR2(30)   NOT NULL,
    review_status         VARCHAR2(30)   NOT NULL,
    review_owner          VARCHAR2(255),
    archived_at           TIMESTAMP(6),
    archived_by           VARCHAR2(255),
    created_at            TIMESTAMP(6)   NOT NULL,
    updated_at            TIMESTAMP(6)   NOT NULL,
    version               NUMBER(19,0)   DEFAULT 0 NOT NULL,
    CONSTRAINT PK_DA_RELEASE_FLOW PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IDX_RF_PROJECT_RELEASE
    ON DA_RELEASE_FLOW (project_id, normalized_release_id);

-- ═══════════════════════════════════════════════════════════════════════════
-- 2. DA_REQUEST — stage-specific grouping of tasks
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE DA_REQUEST (
    id                          VARCHAR2(36)   NOT NULL,
    release_flow_id             VARCHAR2(36)   NOT NULL,
    stage                       VARCHAR2(10)   NOT NULL,
    attempt_number              NUMBER(10,0)   DEFAULT 1 NOT NULL,
    request_status              VARCHAR2(30)   NOT NULL,
    snow_group                  VARCHAR2(255),
    application                 VARCHAR2(255),
    agent                       VARCHAR2(255),
    owner                       VARCHAR2(255),
    site                        VARCHAR2(100),
    created_by                  VARCHAR2(255),
    estimated_remaining_minutes NUMBER(10,0),
    archived_at                 TIMESTAMP(6),
    archived_by                 VARCHAR2(255),
    created_at                  TIMESTAMP(6)   NOT NULL,
    updated_at                  TIMESTAMP(6)   NOT NULL,
    version                     NUMBER(19,0)   DEFAULT 0 NOT NULL,
    CONSTRAINT PK_DA_REQUEST PRIMARY KEY (id),
    CONSTRAINT FK_REQ_RELEASE_FLOW
        FOREIGN KEY (release_flow_id) REFERENCES DA_RELEASE_FLOW (id)
);

CREATE INDEX IDX_REQ_FLOW_STAGE
    ON DA_REQUEST (release_flow_id, stage);

CREATE UNIQUE INDEX UK_REQ_FLOW_STAGE_ATTEMPT
    ON DA_REQUEST (release_flow_id, stage, attempt_number);

-- ═══════════════════════════════════════════════════════════════════════════
-- 3. DA_TASK — atomic execution unit
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE DA_TASK (
    id                     VARCHAR2(36)   NOT NULL,
    request_id             VARCHAR2(36)   NOT NULL,
    task_group_id          VARCHAR2(255)  NOT NULL,
    task_group_name        VARCHAR2(255)  NOT NULL,
    step_seq               NUMBER(10,0)   NOT NULL,
    task_name              VARCHAR2(255)  NOT NULL,
    execution_type         VARCHAR2(10)   NOT NULL,
    task_status            VARCHAR2(30)   NOT NULL,
    critical_flag          NUMBER(1,0)    DEFAULT 0 NOT NULL,
    input_parameters       CLOB,
    expected_output        CLOB,
    owner                  VARCHAR2(255),
    planned_start_time     TIMESTAMP(6),
    planned_end_time       TIMESTAMP(6),
    import_metadata        CLOB,
    current_result_summary CLOB,
    latest_execution_id    VARCHAR2(36),
    active_execution_id    VARCHAR2(36),
    assignee_user_id       VARCHAR2(255),
    capability_type        VARCHAR2(30),
    capability_id          VARCHAR2(255),
    capability_version     VARCHAR2(128),
    repository_id          VARCHAR2(255),
    repository_provider    VARCHAR2(128),
    repository_url         VARCHAR2(2048),
    repository_branch      VARCHAR2(1024),
    repository_commit      VARCHAR2(255),
    start_time             TIMESTAMP(6),
    end_time               TIMESTAMP(6),
    created_at             TIMESTAMP(6)   NOT NULL,
    last_updated_at        TIMESTAMP(6)   NOT NULL,
    -- V15: MVP Foundation Seams
    risk_level             VARCHAR2(4)    DEFAULT 'L2' NOT NULL,
    expected_sla_minutes   NUMBER(10),
    -- V18: per-agent custom fields
    custom_fields          CLOB,
    version                NUMBER(19,0)   DEFAULT 0 NOT NULL,
    CONSTRAINT PK_DA_TASK PRIMARY KEY (id),
    CONSTRAINT FK_TASK_REQUEST
        FOREIGN KEY (request_id) REFERENCES DA_REQUEST (id),
    CONSTRAINT CK_TASK_CRITICAL_FLAG
        CHECK (critical_flag IN (0, 1))
);

CREATE INDEX IDX_TASK_REQUEST
    ON DA_TASK (request_id);

CREATE INDEX IDX_TASK_STATUS
    ON DA_TASK (task_status);

CREATE INDEX IDX_TASK_GROUP_SEQ
    ON DA_TASK (task_group_id, step_seq);

CREATE INDEX IDX_TASK_EXECUTION_TYPE
    ON DA_TASK (execution_type);

CREATE INDEX IDX_TASK_ACTIVE_EXEC
    ON DA_TASK (active_execution_id);

CREATE INDEX IDX_TASK_CAPABILITY
    ON DA_TASK (capability_type, capability_id);

-- ═══════════════════════════════════════════════════════════════════════════
-- 4. DA_TASK_EXECUTION_HISTORY — immutable record of each attempt
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE DA_TASK_EXECUTION_HISTORY (
    id                    VARCHAR2(36)   NOT NULL,
    task_id               VARCHAR2(36)   NOT NULL,
    attempt_number        NUMBER(10,0)   NOT NULL,
    execution_status      VARCHAR2(30)   NOT NULL,
    input_snapshot        CLOB,
    result_summary        CLOB,
    result_logs           CLOB,
    start_time            TIMESTAMP(6)   NOT NULL,
    end_time              TIMESTAMP(6),
    external_system_type  VARCHAR2(30),
    external_execution_id VARCHAR2(255),
    external_job_url      VARCHAR2(2000),
    submitted_at          TIMESTAMP(6),
    submission_status     VARCHAR2(30),
    submission_message    VARCHAR2(2000),
    config_application    VARCHAR2(255),
    config_snow_group     VARCHAR2(255),
    config_agent          VARCHAR2(255),
    -- V15: MVP Foundation Seams
    actor_kind            VARCHAR2(20)   DEFAULT 'HUMAN' NOT NULL,
    actor_ref             VARCHAR2(255),
    -- V21: Atlas Integration immutable execution facts
    integration_managed   NUMBER(1,0)    DEFAULT 0 NOT NULL,
    user_id               VARCHAR2(255),
    user_display_name     VARCHAR2(255),
    client_application_id VARCHAR2(255),
    client_type           VARCHAR2(30),
    client_version        VARCHAR2(128),
    capability_type       VARCHAR2(30),
    capability_id         VARCHAR2(255),
    capability_version    VARCHAR2(128),
    project_id            VARCHAR2(255),
    project_name          VARCHAR2(255),
    repository_id         VARCHAR2(255),
    repository_provider   VARCHAR2(128),
    repository_url        VARCHAR2(2048),
    repository_branch     VARCHAR2(1024),
    repository_commit     VARCHAR2(255),
    duration_ms           NUMBER(19,0),
    artifact_count        NUMBER(10,0)    DEFAULT 0 NOT NULL,
    failure_code          VARCHAR2(128),
    failure_message       VARCHAR2(2000),
    failure_retryable     NUMBER(1,0),
    cancellation_reason   VARCHAR2(2000),
    correlation_id        VARCHAR2(64),
    last_event_at         TIMESTAMP(6),
    version               NUMBER(19,0)    DEFAULT 0 NOT NULL,
    CONSTRAINT PK_DA_TASK_EXEC_HISTORY PRIMARY KEY (id),
    CONSTRAINT FK_TEH_TASK
        FOREIGN KEY (task_id) REFERENCES DA_TASK (id),
    CONSTRAINT CK_TEH_INTEGRATION_MANAGED CHECK (integration_managed IN (0, 1)),
    CONSTRAINT CK_TEH_FAILURE_RETRYABLE CHECK (failure_retryable IS NULL OR failure_retryable IN (0, 1)),
    CONSTRAINT CK_TEH_CLIENT_TYPE CHECK (
        client_type IS NULL OR client_type IN ('COPILOT', 'OPENCODE', 'KIRO', 'MANUAL', 'PIPELINE')
    ),
    CONSTRAINT CK_TEH_TERMINAL_FIELDS CHECK (
        integration_managed = 0
        OR (execution_status = 'Running' AND end_time IS NULL)
        OR (execution_status IN ('Completed', 'Failed', 'Timed_Out', 'Cancelled') AND end_time IS NOT NULL)
    ),
    CONSTRAINT CK_TEH_FAILURE_FIELDS CHECK (
        integration_managed = 0 OR execution_status <> 'Failed' OR failure_code IS NOT NULL
    ),
    CONSTRAINT UK_TEH_ID_TASK UNIQUE (id, task_id)
);

ALTER TABLE DA_TASK ADD CONSTRAINT FK_TASK_ACTIVE_EXECUTION
    FOREIGN KEY (active_execution_id, id)
    REFERENCES DA_TASK_EXECUTION_HISTORY (id, task_id);

CREATE UNIQUE INDEX IDX_TEH_TASK_ATTEMPT
    ON DA_TASK_EXECUTION_HISTORY (task_id, attempt_number);

CREATE INDEX IDX_TEH_TASK
    ON DA_TASK_EXECUTION_HISTORY (task_id);

CREATE INDEX IDX_TEH_INTEGRATION_TIME
    ON DA_TASK_EXECUTION_HISTORY (integration_managed, start_time);

CREATE INDEX IDX_TEH_CAPABILITY
    ON DA_TASK_EXECUTION_HISTORY (capability_type, capability_id, capability_version);

CREATE INDEX IDX_TEH_SCOPE
    ON DA_TASK_EXECUTION_HISTORY (config_application, config_snow_group, config_agent);

CREATE INDEX IDX_TEH_PROJECT_CLIENT
    ON DA_TASK_EXECUTION_HISTORY (project_id, client_type);

CREATE UNIQUE INDEX UK_TEH_ONE_RUNNING_INTEGRATION
    ON DA_TASK_EXECUTION_HISTORY (
        CASE WHEN integration_managed = 1 AND execution_status = 'Running' THEN task_id END
    );

-- ═══════════════════════════════════════════════════════════════════════════
-- 5. DA_CONFIGURATION_ITEM — raw key-value configuration
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE DA_CONFIGURATION_ITEM (
    config_key   VARCHAR2(100)  NOT NULL,
    config_value VARCHAR2(2000) NOT NULL,
    description  VARCHAR2(500),
    updated_by   VARCHAR2(255)  NOT NULL,
    updated_at   TIMESTAMP(6)   NOT NULL,
    CONSTRAINT PK_DA_CONFIGURATION_ITEM PRIMARY KEY (config_key)
);

-- ═══════════════════════════════════════════════════════════════════════════
-- 6. DA_CONFIGURATION_COMPONENT — scoped component metadata
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE DA_CONFIGURATION_COMPONENT (
    id                  VARCHAR2(36)   NOT NULL,
    component_id        VARCHAR2(50)   NOT NULL,
    scope_key           VARCHAR2(500)  NOT NULL,
    system_type         VARCHAR2(30)   NOT NULL,
    display_name        VARCHAR2(255)  NOT NULL,
    area                VARCHAR2(100)  NOT NULL,
    application         VARCHAR2(255),
    snow_group          VARCHAR2(255),
    agent               VARCHAR2(255),
    service_endpoint    VARCHAR2(2000),
    service_user        VARCHAR2(255),
    credential_value    VARCHAR2(4000),
    track_service_user  NUMBER(1)      NOT NULL,
    track_credential    NUMBER(1)      NOT NULL,
    description         VARCHAR2(500),
    updated_by          VARCHAR2(255)  NOT NULL,
    updated_at          TIMESTAMP(6)   NOT NULL,
    CONSTRAINT PK_DA_CONFIGURATION_COMPONENT PRIMARY KEY (id)
);

CREATE INDEX IDX_DCC_COMPONENT
    ON DA_CONFIGURATION_COMPONENT (component_id);

CREATE INDEX IDX_DCC_SYSTEM_TYPE
    ON DA_CONFIGURATION_COMPONENT (system_type);

CREATE INDEX IDX_DCC_SCOPE
    ON DA_CONFIGURATION_COMPONENT (application, snow_group, agent);

CREATE UNIQUE INDEX UK_DCC_COMPONENT_SCOPE
    ON DA_CONFIGURATION_COMPONENT (component_id, scope_key);

-- ═══════════════════════════════════════════════════════════════════════════
-- 7. DA_AUDIT_LOG_ENTRY — immutable audit trail
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE DA_AUDIT_LOG_ENTRY (
    id              VARCHAR2(36)   NOT NULL,
    operator_id     VARCHAR2(255)  NOT NULL,
    operator_role   VARCHAR2(50)   NOT NULL,
    -- V15: MVP Foundation Seams
    actor_kind      VARCHAR2(20)   DEFAULT 'HUMAN' NOT NULL,
    actor_ref       VARCHAR2(255),
    -- V16: infrastructure observability
    correlation_id  VARCHAR2(64),
    action_type     VARCHAR2(50)   NOT NULL,
    timestamp       TIMESTAMP(6)   NOT NULL,
    release_flow_id VARCHAR2(36),
    request_id      VARCHAR2(36),
    task_id         VARCHAR2(36),
    application     VARCHAR2(255),
    snow_group      VARCHAR2(255),
    agent           VARCHAR2(255),
    context_payload CLOB,
    CONSTRAINT PK_DA_AUDIT_LOG_ENTRY PRIMARY KEY (id)
);

CREATE INDEX IDX_ALE_TIMESTAMP
    ON DA_AUDIT_LOG_ENTRY (timestamp);

CREATE INDEX IDX_ALE_OPERATOR
    ON DA_AUDIT_LOG_ENTRY (operator_id);

CREATE INDEX IDX_ALE_ACTION_TYPE
    ON DA_AUDIT_LOG_ENTRY (action_type);

CREATE INDEX IDX_ALE_RELEASE_FLOW
    ON DA_AUDIT_LOG_ENTRY (release_flow_id);

CREATE INDEX IDX_ALE_APPLICATION
    ON DA_AUDIT_LOG_ENTRY (application);

CREATE INDEX IDX_ALE_SNOW_GROUP
    ON DA_AUDIT_LOG_ENTRY (snow_group);

CREATE INDEX IDX_ALE_AGENT
    ON DA_AUDIT_LOG_ENTRY (agent);

CREATE INDEX IDX_ALE_CORRELATION
    ON DA_AUDIT_LOG_ENTRY (correlation_id);

-- ═══════════════════════════════════════════════════════════════════════════
-- 8. DA_ACCESS_GRANT — deny-by-default product entry
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE DA_ACCESS_GRANT (
    employee_id           VARCHAR2(255)  NOT NULL,
    display_name_snapshot VARCHAR2(255)  NOT NULL,
    grant_status          VARCHAR2(30)   NOT NULL,
    assigned_roles        CLOB           NOT NULL,
    scope_grants          CLOB           NOT NULL,
    note                  VARCHAR2(1000),
    last_login_at         TIMESTAMP(6),
    created_by            VARCHAR2(255)  NOT NULL,
    created_at            TIMESTAMP(6)   NOT NULL,
    updated_by            VARCHAR2(255)  NOT NULL,
    updated_at            TIMESTAMP(6)   NOT NULL,
    version               NUMBER(19,0)   DEFAULT 0 NOT NULL,
    CONSTRAINT PK_DA_ACCESS_GRANT PRIMARY KEY (employee_id)
);

CREATE INDEX IDX_AG_STATUS
    ON DA_ACCESS_GRANT (grant_status);

-- ═══════════════════════════════════════════════════════════════════════════
-- 9. DA_SCOPE_DIRECTORY — curated application / SNOW group / agent choices
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE DA_SCOPE_DIRECTORY (
    id          VARCHAR2(36)   NOT NULL,
    scope_key   VARCHAR2(500)  NOT NULL,
    application VARCHAR2(255)  NOT NULL,
    snow_group  VARCHAR2(255),
    agent       VARCHAR2(255),
    updated_by  VARCHAR2(255)  NOT NULL,
    updated_at  TIMESTAMP(6)   NOT NULL,
    CONSTRAINT PK_DA_SCOPE_DIRECTORY PRIMARY KEY (id)
);

CREATE INDEX IDX_DSD_APPLICATION
    ON DA_SCOPE_DIRECTORY (application);

CREATE INDEX IDX_DSD_APP_SNOW
    ON DA_SCOPE_DIRECTORY (application, snow_group);

CREATE INDEX IDX_DSD_APP_SNOW_AGENT
    ON DA_SCOPE_DIRECTORY (application, snow_group, agent);

CREATE UNIQUE INDEX UK_DSD_SCOPE_KEY
    ON DA_SCOPE_DIRECTORY (scope_key);

-- ═══════════════════════════════════════════════════════════════════════════
-- 10. DA_OUTBOX_EVENT — transactional outbox for domain events
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE DA_OUTBOX_EVENT (
    id              VARCHAR2(36)   NOT NULL,
    occurred_at     TIMESTAMP(6)   NOT NULL,
    event_type      VARCHAR2(100)  NOT NULL,
    aggregate_type  VARCHAR2(100),
    aggregate_id    VARCHAR2(36),
    correlation_id  VARCHAR2(64),
    payload         CLOB,
    status          VARCHAR2(20)   DEFAULT 'PENDING' NOT NULL,
    dispatched_at   TIMESTAMP(6),
    version         NUMBER(19)     DEFAULT 0         NOT NULL,
    CONSTRAINT PK_DA_OUTBOX_EVENT PRIMARY KEY (id)
);

CREATE INDEX IDX_OUTBOX_STATUS_OCC
    ON DA_OUTBOX_EVENT (status, occurred_at);

CREATE INDEX IDX_OUTBOX_AGG
    ON DA_OUTBOX_EVENT (aggregate_type, aggregate_id);

CREATE INDEX IDX_OUTBOX_CORR
    ON DA_OUTBOX_EVENT (correlation_id);

-- ═══════════════════════════════════════════════════════════════════════════
-- 11. DA_SERVICE_DIRECTORY_CATALOG — Service Directory catalog document
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE DA_SERVICE_DIRECTORY_CATALOG (
    id          VARCHAR2(36)   NOT NULL,
    payload     CLOB           NOT NULL,
    version     NUMBER(19,0)   DEFAULT 0 NOT NULL,
    updated_by  VARCHAR2(64),
    updated_at  TIMESTAMP(6)   NOT NULL,
    created_at  TIMESTAMP(6)   NOT NULL,
    CONSTRAINT PK_DA_SERVICE_DIRECTORY_CATALOG PRIMARY KEY (id)
);

-- ═══════════════════════════════════════════════════════════════════════════
-- 12. DA_EXECUTION_EVENT — append-only Integration lifecycle event ledger
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE DA_EXECUTION_EVENT (
    id                    VARCHAR2(36)   NOT NULL,
    execution_id          VARCHAR2(36)   NOT NULL,
    task_id               VARCHAR2(36)   NOT NULL,
    event_type            VARCHAR2(40)   NOT NULL,
    sequence_number       NUMBER(19,0),
    percentage            NUMBER(10,0),
    message               VARCHAR2(2000),
    details_json          CLOB,
    actor_kind            VARCHAR2(20)   NOT NULL,
    actor_id              VARCHAR2(255)  NOT NULL,
    client_application_id VARCHAR2(255)  NOT NULL,
    correlation_id        VARCHAR2(64),
    client_timestamp      TIMESTAMP(6),
    received_at           TIMESTAMP(6)   NOT NULL,
    CONSTRAINT PK_DA_EXECUTION_EVENT PRIMARY KEY (id),
    CONSTRAINT FK_EE_EXEC_TASK FOREIGN KEY (execution_id, task_id)
        REFERENCES DA_TASK_EXECUTION_HISTORY (id, task_id),
    CONSTRAINT FK_EE_TASK FOREIGN KEY (task_id) REFERENCES DA_TASK (id),
    CONSTRAINT CK_EE_SEQUENCE CHECK (sequence_number IS NULL OR sequence_number > 0),
    CONSTRAINT CK_EE_PERCENTAGE CHECK (percentage IS NULL OR percentage BETWEEN 0 AND 100)
);

CREATE INDEX IDX_EE_EXEC_RECEIVED
    ON DA_EXECUTION_EVENT (execution_id, received_at);

CREATE INDEX IDX_EE_CORRELATION
    ON DA_EXECUTION_EVENT (correlation_id);

-- Only client progress events participate in sequence uniqueness; lifecycle
-- and audit events keep sequence_number NULL.
CREATE UNIQUE INDEX UK_EE_EXEC_SEQUENCE
    ON DA_EXECUTION_EVENT (
        CASE WHEN sequence_number IS NOT NULL THEN execution_id END,
        CASE WHEN sequence_number IS NOT NULL THEN sequence_number END
    );

-- ═══════════════════════════════════════════════════════════════════════════
-- 13. DA_INTEGRATION_ARTIFACT — bounded immutable Artifact metadata/content
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE DA_INTEGRATION_ARTIFACT (
    id                    VARCHAR2(36)   NOT NULL,
    task_id               VARCHAR2(36)   NOT NULL,
    execution_id          VARCHAR2(36)   NOT NULL,
    artifact_role         VARCHAR2(20)   NOT NULL,
    artifact_kind         VARCHAR2(128)  NOT NULL,
    artifact_name         VARCHAR2(255)  NOT NULL,
    media_type            VARCHAR2(255)  NOT NULL,
    size_bytes            NUMBER(19,0)   NOT NULL,
    sha256                VARCHAR2(64)   NOT NULL,
    source_path           VARCHAR2(1024),
    storage_mode          VARCHAR2(20)   NOT NULL,
    content_blob          BLOB,
    reference_artifact_id VARCHAR2(36),
    created_by            VARCHAR2(255)  NOT NULL,
    client_application_id VARCHAR2(255)  NOT NULL,
    correlation_id        VARCHAR2(64),
    content_expires_at    TIMESTAMP(6),
    content_purged_at     TIMESTAMP(6),
    legal_hold            NUMBER(1,0) DEFAULT 0 NOT NULL,
    created_at            TIMESTAMP(6)   NOT NULL,
    version               NUMBER(19,0) DEFAULT 0 NOT NULL,
    CONSTRAINT PK_DA_INTEGRATION_ARTIFACT PRIMARY KEY (id),
    CONSTRAINT FK_IA_TASK FOREIGN KEY (task_id) REFERENCES DA_TASK (id),
    CONSTRAINT FK_IA_EXEC_TASK FOREIGN KEY (execution_id, task_id)
        REFERENCES DA_TASK_EXECUTION_HISTORY (id, task_id),
    CONSTRAINT FK_IA_REFERENCE FOREIGN KEY (reference_artifact_id) REFERENCES DA_INTEGRATION_ARTIFACT (id),
    CONSTRAINT CK_IA_SIZE CHECK (size_bytes >= 0),
    CONSTRAINT CK_IA_SHA256 CHECK (REGEXP_LIKE(sha256, '^[0-9a-f]{64}$')),
    CONSTRAINT CK_IA_ROLE CHECK (artifact_role IN ('INPUT', 'OUTPUT', 'EVIDENCE')),
    CONSTRAINT CK_IA_STORAGE_MODE CHECK (storage_mode IN ('UPLOAD', 'REFERENCE')),
    CONSTRAINT CK_IA_LEGAL_HOLD CHECK (legal_hold IN (0, 1)),
    CONSTRAINT CK_IA_PURGED_CONTENT CHECK (content_purged_at IS NULL OR content_blob IS NULL),
    CONSTRAINT CK_IA_CONTENT CHECK (
        (storage_mode = 'UPLOAD' AND reference_artifact_id IS NULL AND content_expires_at IS NOT NULL)
        OR
        (storage_mode = 'REFERENCE' AND reference_artifact_id IS NOT NULL
            AND content_blob IS NULL AND content_expires_at IS NULL)
    )
);

CREATE INDEX IDX_IA_EXECUTION
    ON DA_INTEGRATION_ARTIFACT (execution_id, created_at);

CREATE INDEX IDX_IA_TASK
    ON DA_INTEGRATION_ARTIFACT (task_id);

CREATE INDEX IDX_IA_DIGEST
    ON DA_INTEGRATION_ARTIFACT (sha256);

CREATE INDEX IDX_IA_RETENTION
    ON DA_INTEGRATION_ARTIFACT (legal_hold, content_expires_at);

-- ═══════════════════════════════════════════════════════════════════════════
-- 14. DA_TASK_INPUT_ARTIFACT / DA_INTEGRATION_REVIEW
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE DA_TASK_INPUT_ARTIFACT (
    id          VARCHAR2(36)   NOT NULL,
    task_id     VARCHAR2(36)   NOT NULL,
    artifact_id VARCHAR2(36)   NOT NULL,
    approved_by VARCHAR2(255)  NOT NULL,
    approved_at TIMESTAMP(6)   NOT NULL,
    CONSTRAINT PK_DA_TASK_INPUT_ARTIFACT PRIMARY KEY (id),
    CONSTRAINT FK_TIA_TASK FOREIGN KEY (task_id) REFERENCES DA_TASK (id),
    CONSTRAINT FK_TIA_ARTIFACT FOREIGN KEY (artifact_id) REFERENCES DA_INTEGRATION_ARTIFACT (id),
    CONSTRAINT UK_TIA_TASK_ARTIFACT UNIQUE (task_id, artifact_id)
);

CREATE TABLE DA_INTEGRATION_REVIEW (
    id                    VARCHAR2(36)   NOT NULL,
    task_id               VARCHAR2(36)   NOT NULL,
    execution_id          VARCHAR2(36)   NOT NULL,
    decision              VARCHAR2(20)   NOT NULL,
    reviewer_id           VARCHAR2(255)  NOT NULL,
    reviewer_display_name VARCHAR2(255)  NOT NULL,
    review_comment        VARCHAR2(2000),
    correlation_id        VARCHAR2(64),
    decided_at            TIMESTAMP(6)   NOT NULL,
    CONSTRAINT PK_DA_INTEGRATION_REVIEW PRIMARY KEY (id),
    CONSTRAINT FK_IR_TASK FOREIGN KEY (task_id) REFERENCES DA_TASK (id),
    CONSTRAINT FK_IR_EXEC_TASK FOREIGN KEY (execution_id, task_id)
        REFERENCES DA_TASK_EXECUTION_HISTORY (id, task_id),
    CONSTRAINT UK_IR_EXECUTION UNIQUE (execution_id),
    CONSTRAINT CK_IR_DECISION CHECK (decision IN ('APPROVED', 'REJECTED', 'SKIPPED'))
);

-- ═══════════════════════════════════════════════════════════════════════════
-- 15. DA_INTEGRATION_IDEMPOTENCY — replay-safe command responses
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE DA_INTEGRATION_IDEMPOTENCY (
    id                    VARCHAR2(36)   NOT NULL,
    principal_id          VARCHAR2(255)  NOT NULL,
    client_application_id VARCHAR2(255)  NOT NULL,
    http_method           VARCHAR2(10)   NOT NULL,
    canonical_path        VARCHAR2(1000) NOT NULL,
    idempotency_key_hash  VARCHAR2(64)   NOT NULL,
    request_fingerprint   VARCHAR2(64)   NOT NULL,
    record_state          VARCHAR2(20)   NOT NULL,
    response_status       NUMBER(10,0),
    response_body         CLOB,
    resource_location     VARCHAR2(1000),
    created_at            TIMESTAMP(6)   NOT NULL,
    completed_at          TIMESTAMP(6),
    expires_at            TIMESTAMP(6),
    version               NUMBER(19,0) DEFAULT 0 NOT NULL,
    CONSTRAINT PK_DA_INTEGRATION_IDEMPOTENCY PRIMARY KEY (id),
    CONSTRAINT CK_II_STATE CHECK (record_state IN ('IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT UK_II_COMMAND UNIQUE (
        principal_id,
        http_method,
        canonical_path,
        idempotency_key_hash
    )
);

CREATE INDEX IDX_II_EXPIRES
    ON DA_INTEGRATION_IDEMPOTENCY (expires_at);
