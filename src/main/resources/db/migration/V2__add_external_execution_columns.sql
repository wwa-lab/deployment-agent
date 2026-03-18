-- V2: Add external execution metadata columns to DA_TASK_EXECUTION_HISTORY
-- These columns support AUTO task submission to Jenkins/Ansible.
-- All columns are nullable (MANUAL tasks will have NULLs).

ALTER TABLE DA_TASK_EXECUTION_HISTORY ADD (
    external_system_type   VARCHAR2(30),
    external_execution_id  VARCHAR2(255),
    external_job_url       VARCHAR2(2000),
    submitted_at           TIMESTAMP,
    submission_status      VARCHAR2(30),
    submission_message     VARCHAR2(2000)
);

COMMENT ON COLUMN DA_TASK_EXECUTION_HISTORY.external_system_type  IS 'External system: JENKINS or ANSIBLE. NULL for MANUAL tasks.';
COMMENT ON COLUMN DA_TASK_EXECUTION_HISTORY.external_execution_id IS 'Build/job ID in the external system.';
COMMENT ON COLUMN DA_TASK_EXECUTION_HISTORY.external_job_url      IS 'URL to view the job in Jenkins/Ansible.';
COMMENT ON COLUMN DA_TASK_EXECUTION_HISTORY.submitted_at          IS 'Timestamp when submission was sent to external system.';
COMMENT ON COLUMN DA_TASK_EXECUTION_HISTORY.submission_status     IS 'Outcome of submission: SUBMITTED or FAILED.';
COMMENT ON COLUMN DA_TASK_EXECUTION_HISTORY.submission_message    IS 'Success or error message from submission attempt.';
