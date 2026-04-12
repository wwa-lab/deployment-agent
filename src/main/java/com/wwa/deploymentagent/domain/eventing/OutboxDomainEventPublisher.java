package com.wwa.deploymentagent.domain.eventing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * OutboxDomainEventPublisher — transactional-outbox implementation of
 * {@link DomainEventPublisher}.
 *
 * <p>Writes a row into {@code DA_OUTBOX_EVENT} inside the caller's
 * transaction. Delivery is the responsibility of a future dispatcher
 * (not yet implemented); rows stay in {@code PENDING} status until it
 * exists. Because the insert is part of the caller's transaction, the
 * event is guaranteed to be durable if and only if the business state
 * change is durable — no event is ever lost to a transient failure
 * between "business update succeeded" and "notification fired".
 *
 * <p>In MVP, rows accumulate and are never consumed. This is intentional:
 * the seam exists so the email notification feature (and any other
 * downstream integration) can be added as pure additive code rather than
 * a cross-cutting refactor.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxDomainEventPublisher implements DomainEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final Clock clock;

    @Override
    public void publish(DomainEvent event) {
        if (event == null) {
            return;
        }
        try {
            OutboxEvent entity = new OutboxEvent();
            Instant occurredAt = event.occurredAt() != null ? event.occurredAt() : clock.instant();
            entity.setOccurredAt(occurredAt);
            entity.setEventType(event.eventType());
            entity.setAggregateType(event.aggregateType());
            entity.setAggregateId(event.aggregateId());
            entity.setCorrelationId(event.correlationId());
            entity.setPayload(event.payload());
            entity.setStatus(OutboxEvent.STATUS_PENDING);
            outboxEventRepository.save(entity);
        } catch (Exception ex) {
            // Mirror the audit-logger policy: never break the business flow
            // because the outbox insert failed. Log loudly so ops can notice.
            // The dispatcher (when it exists) will have its own monitoring.
            log.warn("[OutboxDomainEventPublisher] Failed to publish event type={} aggregateId={}: {}",
                    event.eventType(), event.aggregateId(), ex.getMessage(), ex);
        }
    }
}
