-- WWA Agent Workspace Hub — Oracle current schema
-- Generated for greenfield UAT / internal environment setup.
-- Use this script for a fresh Oracle schema.
-- Do not run V2-V19 incremental scripts on top of this file for a brand-new database,
-- because this script already includes the current end-state columns from V2 through V19.

-- ═══════════════════════════════════════════════════════════════════════════
-- 1. DA_RELEASE_FLOW — root aggregate
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE DA_RELEASE_FLOW (
    id                    VARCHAR2(36)   NOT NULL,
    project_id            VARCHAR2(255)  NOT NULL,
    project_name          VARCHAR2(255)  NOT NULL,
    release_id            VARCHAR2(255),
    normalized_release_id VARCHAR2(255)  NOT NULL,
    current_stage         VARCHAR2(64)   NOT NULL,
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
    stage                       VARCHAR2(64)   NOT NULL,
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
    start_time             TIMESTAMP(6),
    end_time               TIMESTAMP(6),
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
    CONSTRAINT PK_DA_TASK_EXEC_HISTORY PRIMARY KEY (id),
    CONSTRAINT FK_TEH_TASK
        FOREIGN KEY (task_id) REFERENCES DA_TASK (id)
);

CREATE UNIQUE INDEX IDX_TEH_TASK_ATTEMPT
    ON DA_TASK_EXECUTION_HISTORY (task_id, attempt_number);

CREATE INDEX IDX_TEH_TASK
    ON DA_TASK_EXECUTION_HISTORY (task_id);

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
