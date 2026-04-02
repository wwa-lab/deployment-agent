CREATE TABLE DA_DEVELOPMENT_SPEC (
    id                VARCHAR2(36)   NOT NULL,
    title             VARCHAR2(255)  NOT NULL,
    module_name       VARCHAR2(255),
    program_type      VARCHAR2(50)   NOT NULL,
    code_style        VARCHAR2(50)   NOT NULL,
    application       VARCHAR2(255),
    snow_group        VARCHAR2(255),
    source_payload    CLOB           NOT NULL,
    generated_payload CLOB,
    generated_content CLOB,
    generated_at      TIMESTAMP(6),
    generated_by      VARCHAR2(255),
    status            VARCHAR2(30)   NOT NULL,
    created_by        VARCHAR2(255)  NOT NULL,
    created_at        TIMESTAMP(6)   NOT NULL,
    updated_by        VARCHAR2(255)  NOT NULL,
    updated_at        TIMESTAMP(6)   NOT NULL,
    version           NUMBER(19) DEFAULT 0 NOT NULL,
    CONSTRAINT PK_DA_DEVELOPMENT_SPEC PRIMARY KEY (id)
);

CREATE INDEX IDX_DSPEC_SCOPE
    ON DA_DEVELOPMENT_SPEC (application, snow_group);

CREATE INDEX IDX_DSPEC_STATUS
    ON DA_DEVELOPMENT_SPEC (status);
