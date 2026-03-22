-- V3: Add stage-level rundown metadata to DA_REQUEST
-- These columns back the Rundown Information panel for SIT/UAT/PROD requests.

ALTER TABLE DA_REQUEST ADD (
    snow_group                  VARCHAR2(255),
    application                 VARCHAR2(255),
    site                        VARCHAR2(100),
    created_by                  VARCHAR2(255),
    estimated_remaining_minutes NUMBER(10)
);

COMMENT ON COLUMN DA_REQUEST.snow_group IS 'External change / SNOW grouping for the stage request.';
COMMENT ON COLUMN DA_REQUEST.application IS 'Application label shown in the rundown panel.';
COMMENT ON COLUMN DA_REQUEST.site IS 'Deployment site / region label for the stage request.';
COMMENT ON COLUMN DA_REQUEST.created_by IS 'User who first created the stage request.';
COMMENT ON COLUMN DA_REQUEST.estimated_remaining_minutes IS 'Editable estimated remaining time for the stage request.';
