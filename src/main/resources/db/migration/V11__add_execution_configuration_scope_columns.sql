ALTER TABLE DA_TASK_EXECUTION_HISTORY
    ADD (
        config_application VARCHAR2(255),
        config_snow_group  VARCHAR2(255),
        config_agent       VARCHAR2(255)
    );
