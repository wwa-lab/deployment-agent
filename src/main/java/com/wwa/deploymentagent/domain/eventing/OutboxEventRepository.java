package com.wwa.deploymentagent.domain.eventing;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for {@link OutboxEvent}. The dispatcher (not yet implemented)
 * will call {@link #findByStatusOrderByOccurredAtAsc} to pull the next
 * batch of pending events for delivery.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    List<OutboxEvent> findByStatusOrderByOccurredAtAsc(String status, Pageable pageable);
}
