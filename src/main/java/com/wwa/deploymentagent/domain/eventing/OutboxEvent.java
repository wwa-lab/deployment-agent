package com.wwa.deploymentagent.domain.eventing;

import com.wwa.deploymentagent.util.JsonAttributeConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * OutboxEvent — transactional outbox row.
 *
 * <p>A row is inserted inside the business transaction that caused the
 * event (e.g. a task decision). A separate relay/dispatcher — not yet
 * implemented in MVP — is responsible for reading rows in {@code PENDING}
 * state, delivering them to downstream consumers (email / webhook / BI),
 * and marking the row {@code DISPATCHED}.
 *
 * <p>Status values:
 * <ul>
 *   <li>{@code PENDING}    — waiting for the dispatcher</li>
 *   <li>{@code DISPATCHED} — successfully delivered</li>
 *   <li>{@code FAILED}     — delivery failed permanently (for audit/retry)</li>
 * </ul>
 *
 * <p>No consumer exists in MVP. See
 * {@code docs/04-architecture/architecture.md} §MVP Foundation Seams for
 * the rationale and the future email-notification integration plan.
 */
@Entity
@Table(
    name = "DA_OUTBOX_EVENT",
    indexes = {
        @Index(name = "IDX_OUTBOX_STATUS_OCC", columnList = "status, occurred_at"),
        @Index(name = "IDX_OUTBOX_AGG", columnList = "aggregate_type, aggregate_id"),
        @Index(name = "IDX_OUTBOX_CORR", columnList = "correlation_id")
    }
)
@Getter
@Setter
public class OutboxEvent {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_DISPATCHED = "DISPATCHED";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /** Dot-separated domain event type, e.g. {@code task.approve}, {@code task.reject}. */
    @Column(name = "event_type", length = 100, nullable = false)
    private String eventType;

    /** Name of the aggregate root the event relates to, e.g. {@code Task}, {@code ReleaseFlow}. */
    @Column(name = "aggregate_type", length = 100)
    private String aggregateType;

    /** ID of the aggregate root the event relates to. */
    @Column(name = "aggregate_id", length = 36)
    private String aggregateId;

    /**
     * Correlation ID from {@code CorrelationIdFilter}, carried through MDC
     * at publish time. Lets the future dispatcher correlate delivery back
     * to the originating HTTP request in audit and server logs.
     */
    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    /** Arbitrary JSON payload describing the event. */
    @Convert(converter = JsonAttributeConverter.class)
    @Column(name = "payload", columnDefinition = "CLOB")
    private Map<String, Object> payload;

    /** One of {@link #STATUS_PENDING}, {@link #STATUS_DISPATCHED}, {@link #STATUS_FAILED}. */
    @Column(name = "status", length = 20, nullable = false)
    private String status = STATUS_PENDING;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (status == null) {
            status = STATUS_PENDING;
        }
    }
}
