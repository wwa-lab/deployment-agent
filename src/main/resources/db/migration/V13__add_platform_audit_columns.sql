ALTER TABLE DA_AUDIT_LOG_ENTRY
    ADD (
        agent_name    VARCHAR2(255),
        target_type   VARCHAR2(100),
        target_id     VARCHAR2(36),
        source_system VARCHAR2(100)
    );
