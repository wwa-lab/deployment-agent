-- V16: Infrastructure debt fixes for observability (day-1 P0 batch).
--
-- This migration is NOT an MVP Foundation Seam. The columns added here are
-- written on every inbound HTTP request from day one, not reserved for
-- future use. See docs/04-architecture/architecture.md §Infrastructure
-- Foundations.

-- ───────────────────────────────────────────────────────────────────────────
-- 1. Correlation ID on audit log entries.
--    Populated by CorrelationIdFilter via SLF4J MDC. Null for entries
--    written by background jobs that run outside an HTTP request context.
-- ───────────────────────────────────────────────────────────────────────────

ALTER TABLE DA_AUDIT_LOG_ENTRY
    ADD (correlation_id VARCHAR2(64));

CREATE INDEX IDX_ALE_CORRELATION
    ON DA_AUDIT_LOG_ENTRY (correlation_id);
