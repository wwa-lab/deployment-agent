-- V15: MVP Foundation Seams for 7×24 + human-in-the-loop decisions.
--
-- Adds data-model hooks that have zero runtime behavior in MVP but reserve the
-- shape we need for future policy-based automation, AI-assisted decisions,
-- and SLA-driven timeouts. None of these columns are read by runtime code yet;
-- they exist so that later phases can add behavior without retrofitting
-- immutable history tables or backfilling millions of rows.
--
-- See docs/04-architecture/architecture.md §MVP Foundation Seams.

-- ───────────────────────────────────────────────────────────────────────────
-- 1. Actor attribution on immutable history tables.
--    MVP writes only 'HUMAN'. Future writes use 'POLICY', 'AI_ASSISTED', 'SYSTEM'.
-- ───────────────────────────────────────────────────────────────────────────

ALTER TABLE DA_AUDIT_LOG_ENTRY
    ADD (
        actor_kind VARCHAR2(20) DEFAULT 'HUMAN' NOT NULL,
        actor_ref  VARCHAR2(255)
    );

ALTER TABLE DA_TASK_EXECUTION_HISTORY
    ADD (
        actor_kind VARCHAR2(20) DEFAULT 'HUMAN' NOT NULL,
        actor_ref  VARCHAR2(255)
    );

-- ───────────────────────────────────────────────────────────────────────────
-- 2. Task risk classification + SLA placeholder.
--    Every existing task is L2 ("human must decide"), matching current behavior.
--    expected_sla_minutes stays NULL until a future release introduces a
--    timeout sweeper.
-- ───────────────────────────────────────────────────────────────────────────

ALTER TABLE DA_TASK
    ADD (
        risk_level           VARCHAR2(4) DEFAULT 'L2' NOT NULL,
        expected_sla_minutes NUMBER(10)
    );
