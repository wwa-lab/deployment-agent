package com.wwa.agenthub.domain.eventing;

import java.time.Instant;
import java.util.Map;

/**
 * DomainEvent — value type passed to {@link DomainEventPublisher#publish}.
 *
 * <p>Events are intentionally thin: they describe <em>what happened</em>
 * in a durable form, not <em>what to do about it</em>. Consumers (the
 * future email dispatcher, webhook relay, BI exporter) decide how to
 * interpret each {@code eventType}.
 *
 * @param eventType      dot-separated type, e.g. {@code task.approve}, {@code task.reject},
 *                       {@code task.awaiting_review}, {@code release_flow.completed}
 * @param aggregateType  name of the aggregate root (e.g. {@code Task}, {@code ReleaseFlow})
 * @param aggregateId    ID of the aggregate root
 * @param occurredAt     when the event happened (publisher will fall back to now if null)
 * @param correlationId  request-scoped correlation from {@code CorrelationIdFilter}; may be null
 *                       for background / scheduled producers
 * @param payload        additional structured context for consumers
 */
public record DomainEvent(
        String eventType,
        String aggregateType,
        String aggregateId,
        Instant occurredAt,
        String correlationId,
        Map<String, Object> payload
) {
}
