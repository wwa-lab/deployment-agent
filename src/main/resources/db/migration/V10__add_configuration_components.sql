CREATE TABLE DA_CONFIGURATION_COMPONENT (
    id                 VARCHAR2(36)   NOT NULL,
    component_id       VARCHAR2(50)   NOT NULL,
    scope_key          VARCHAR2(500)  NOT NULL,
    system_type        VARCHAR2(30)   NOT NULL,
    display_name       VARCHAR2(255)  NOT NULL,
    area               VARCHAR2(100)  NOT NULL,
    application        VARCHAR2(255),
    snow_group         VARCHAR2(255),
    agent              VARCHAR2(255),
    service_endpoint   VARCHAR2(2000),
    service_user       VARCHAR2(255),
    credential_value   VARCHAR2(4000),
    track_service_user NUMBER(1)      NOT NULL,
    track_credential   NUMBER(1)      NOT NULL,
    description        VARCHAR2(500),
    updated_by         VARCHAR2(255)  NOT NULL,
    updated_at         TIMESTAMP(6)   NOT NULL,
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
