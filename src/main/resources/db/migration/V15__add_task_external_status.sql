-- V15: Denormalize external execution status onto DA_TASK for fast UI rendering.
-- Kept in sync by AutoExecutionService (submit) and ExternalExecutionMonitorService (poll).

ALTER TABLE DA_TASK ADD (
    external_status VARCHAR2(50)
);

COMMENT ON COLUMN DA_TASK.external_status IS 'Denormalized external execution state: QUEUED, RUNNING, WAITING_APPROVAL, SUCCEEDED, FAILED, ABORTED, TIMED_OUT, UNKNOWN. NULL for MANUAL tasks or tasks not yet submitted.';
