-- V17: MVP Foundation Seam — transactional outbox for domain events.
--
-- Rows are written by OutboxDomainEventPublisher inside the caller's
-- business transaction so that event persistence is atomic with the
-- state change that produced it. No consumer exists in MVP; the future
-- email notification dispatcher (and any other downstream integrations)
-- will read rows in PENDING status and mark them DISPATCHED.
--
-- See docs/04-architecture/architecture.md §MVP Foundation Seams.

CREATE TABLE DA_OUTBOX_EVENT (
    id              VARCHAR2(36)   NOT NULL,
    occurred_at     TIMESTAMP(6)   NOT NULL,
    event_type      VARCHAR2(100)  NOT NULL,
    aggregate_type  VARCHAR2(100),
    aggregate_id    VARCHAR2(36),
    correlation_id  VARCHAR2(64),
    payload         CLOB,
    status          VARCHAR2(20) DEFAULT 'PENDING' NOT NULL,
    dispatched_at   TIMESTAMP(6),
    version         NUMBER(19)   DEFAULT 0        NOT NULL,
    CONSTRAINT PK_DA_OUTBOX_EVENT PRIMARY KEY (id)
);

CREATE INDEX IDX_OUTBOX_STATUS_OCC
    ON DA_OUTBOX_EVENT (status, occurred_at);

CREATE INDEX IDX_OUTBOX_AGG
    ON DA_OUTBOX_EVENT (aggregate_type, aggregate_id);

CREATE INDEX IDX_OUTBOX_CORR
    ON DA_OUTBOX_EVENT (correlation_id);
