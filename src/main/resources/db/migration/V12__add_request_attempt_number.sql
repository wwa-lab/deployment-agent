-- V12: Allow multiple deployment requests per release-flow stage via attempt numbering.

ALTER TABLE DA_REQUEST ADD (
    attempt_number NUMBER(10,0) DEFAULT 1 NOT NULL
);

CREATE UNIQUE INDEX UK_REQ_FLOW_STAGE_ATTEMPT
    ON DA_REQUEST (release_flow_id, stage, attempt_number);

COMMENT ON COLUMN DA_REQUEST.attempt_number IS
    'Attempt number within the same release flow + stage; starts at 1 and increments for each new stage rerun upload.';
