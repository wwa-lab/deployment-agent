-- V21: Agent-neutral Atlas Integration control-plane capabilities.
-- Extends the existing Task and TaskExecutionHistory aggregates; it does not
-- create a competing Task/Execution authority and stores no raw bearer token.

ALTER TABLE DA_TASK ADD (
    active_execution_id  VARCHAR2(36),
    assignee_user_id     VARCHAR2(255),
    capability_type      VARCHAR2(30),
    capability_id        VARCHAR2(255),
    capability_version   VARCHAR2(128),
    repository_id        VARCHAR2(255),
    repository_provider  VARCHAR2(128),
    repository_url       VARCHAR2(2048),
    repository_branch    VARCHAR2(1024),
    repository_commit    VARCHAR2(255),
    created_at            TIMESTAMP(6)
);

UPDATE DA_TASK
SET created_at = COALESCE(start_time, last_updated_at, SYSTIMESTAMP)
WHERE created_at IS NULL;

ALTER TABLE DA_TASK MODIFY (created_at NOT NULL);

CREATE INDEX IDX_TASK_ACTIVE_EXEC
    ON DA_TASK (active_execution_id);

CREATE INDEX IDX_TASK_CAPABILITY
    ON DA_TASK (capability_type, capability_id);

ALTER TABLE DA_TASK_EXECUTION_HISTORY ADD (
    integration_managed   NUMBER(1,0) DEFAULT 0 NOT NULL,
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
    artifact_count        NUMBER(10,0) DEFAULT 0 NOT NULL,
    failure_code          VARCHAR2(128),
    failure_message       VARCHAR2(2000),
    failure_retryable     NUMBER(1,0),
    cancellation_reason   VARCHAR2(2000),
    correlation_id        VARCHAR2(64),
    last_event_at         TIMESTAMP(6),
    version               NUMBER(19,0) DEFAULT 0 NOT NULL,
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

CREATE INDEX IDX_TEH_INTEGRATION_TIME
    ON DA_TASK_EXECUTION_HISTORY (integration_managed, start_time);

CREATE INDEX IDX_TEH_CAPABILITY
    ON DA_TASK_EXECUTION_HISTORY (capability_type, capability_id, capability_version);

CREATE INDEX IDX_TEH_SCOPE
    ON DA_TASK_EXECUTION_HISTORY (config_application, config_snow_group, config_agent);

CREATE INDEX IDX_TEH_PROJECT_CLIENT
    ON DA_TASK_EXECUTION_HISTORY (project_id, client_type);

-- Only Integration-managed Running rows participate, proving one active
-- execution per Task independently of application locks.
CREATE UNIQUE INDEX UK_TEH_ONE_RUNNING_INTEGRATION
    ON DA_TASK_EXECUTION_HISTORY (
        CASE WHEN integration_managed = 1 AND execution_status = 'Running' THEN task_id END
    );

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

-- Oracle function-based uniqueness applies only to client progress events.
-- Lifecycle/audit events intentionally carry a NULL sequence_number.
CREATE UNIQUE INDEX UK_EE_EXEC_SEQUENCE
    ON DA_EXECUTION_EVENT (
        CASE WHEN sequence_number IS NOT NULL THEN execution_id END,
        CASE WHEN sequence_number IS NOT NULL THEN sequence_number END
    );

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
