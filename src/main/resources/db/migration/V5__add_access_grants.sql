CREATE TABLE DA_ACCESS_GRANT (
    employee_id           VARCHAR2(255) PRIMARY KEY,
    display_name_snapshot VARCHAR2(255) NOT NULL,
    grant_status          VARCHAR2(30) NOT NULL,
    assigned_roles        CLOB NOT NULL,
    note                  VARCHAR2(1000),
    last_login_at         TIMESTAMP,
    created_by            VARCHAR2(255) NOT NULL,
    created_at            TIMESTAMP NOT NULL,
    updated_by            VARCHAR2(255) NOT NULL,
    updated_at            TIMESTAMP NOT NULL,
    version               NUMBER(19) DEFAULT 0 NOT NULL
);

CREATE INDEX IDX_AG_STATUS ON DA_ACCESS_GRANT (grant_status);

COMMENT ON COLUMN DA_ACCESS_GRANT.employee_id IS 'Enterprise employee identifier and primary key for product authorization.';
COMMENT ON COLUMN DA_ACCESS_GRANT.display_name_snapshot IS 'Last known display name from enterprise identity source.';
COMMENT ON COLUMN DA_ACCESS_GRANT.grant_status IS 'Authorization lifecycle state: ACTIVE or SUSPENDED.';
COMMENT ON COLUMN DA_ACCESS_GRANT.assigned_roles IS 'JSON array of assigned Deployment Agent roles.';
COMMENT ON COLUMN DA_ACCESS_GRANT.note IS 'Optional admin note explaining the grant state or role assignment.';
