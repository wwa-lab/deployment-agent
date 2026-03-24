-- V6: Add agent scope to DA_REQUEST so multi-agent runtimes can be managed per rundown.

ALTER TABLE DA_REQUEST ADD (
    agent VARCHAR2(255)
);

COMMENT ON COLUMN DA_REQUEST.agent IS 'Agent label used to scope the stage request for multi-agent runtime support.';
