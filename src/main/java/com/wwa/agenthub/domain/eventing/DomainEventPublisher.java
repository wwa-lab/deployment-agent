package com.wwa.agenthub.domain.eventing;

/**
 * DomainEventPublisher — the single integration point business services
 * use to publish a {@link DomainEvent} for downstream consumption.
 *
 * <p>MVP implementation: {@link OutboxDomainEventPublisher}, which inserts
 * a row into {@code DA_OUTBOX_EVENT} inside the caller's transaction.
 * No consumer exists in MVP — rows accumulate in {@code PENDING} state and
 * nothing reads them. This is intentional: the scaffolding is in place so
 * that the future email notification feature, and any other downstream
 * integration, is pure additive code (a dispatcher + a consumer) rather
 * than a cross-cutting refactor of every state-transition call site.
 *
 * <p>See {@code docs/04-architecture/architecture.md} §MVP Foundation Seams.
 */
public interface DomainEventPublisher {

    /**
     * Publish a domain event. Must be called inside the caller's existing
     * {@code @Transactional} method so that event persistence is atomic
     * with the business state change that produced it (transactional
     * outbox pattern).
     *
     * @param event the domain event to publish; must not be null
     */
    void publish(DomainEvent event);
}
