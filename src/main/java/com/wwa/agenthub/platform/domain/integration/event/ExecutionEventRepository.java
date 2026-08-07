package com.wwa.agenthub.platform.domain.integration.event;

import com.wwa.agenthub.contracts.enums.ExecutionEventType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExecutionEventRepository extends JpaRepository<ExecutionEvent, String> {

    List<ExecutionEvent> findByExecutionIdOrderByReceivedAtAsc(String executionId);

    long countByExecutionIdAndSequenceNumber(String executionId, Long sequenceNumber);

    long countByExecutionIdAndEventType(String executionId, ExecutionEventType eventType);
}
