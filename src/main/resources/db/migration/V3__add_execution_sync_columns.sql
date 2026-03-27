-- V3: Add external status synchronization columns to DA_TASK_EXECUTION_HISTORY
-- These columns support polling-based completion sync (EXE-001).
-- All columns are nullable (MANUAL tasks and unsynced rows have NULLs).

ALTER TABLE DA_TASK_EXECUTION_HISTORY ADD (
    external_status         VARCHAR2(50),
    external_status_message VARCHAR2(2000),
    external_log_url        VARCHAR2(2000),
    external_approval_url   VARCHAR2(2000),
    last_synced_at          TIMESTAMP
);

COMMENT ON COLUMN DA_TASK_EXECUTION_HISTORY.external_status         IS 'Normalized remote state: QUEUED, RUNNING, WAITING_APPROVAL, SUCCEEDED, FAILED, ABORTED, TIMED_OUT, UNKNOWN.';
COMMENT ON COLUMN DA_TASK_EXECUTION_HISTORY.external_status_message IS 'Human-readable explanation of the current remote state.';
COMMENT ON COLUMN DA_TASK_EXECUTION_HISTORY.external_log_url        IS 'Direct click-through to the remote console or log page.';
COMMENT ON COLUMN DA_TASK_EXECUTION_HISTORY.external_approval_url   IS 'Direct click-through to the remote approval page (workflow approvals only).';
COMMENT ON COLUMN DA_TASK_EXECUTION_HISTORY.last_synced_at          IS 'Timestamp of the last successful poll-based state refresh.';
