package com.wwa.agenthub.platform.domain.integration.event;

import com.wwa.agenthub.contracts.enums.ActorKind;
import com.wwa.agenthub.contracts.enums.ExecutionEventType;
import com.wwa.agenthub.domain.task.TaskExecutionHistory;
import com.wwa.agenthub.platform.domain.integration.IntegrationApiException;
import com.wwa.agenthub.platform.domain.integration.auth.IntegrationActor;
import com.wwa.agenthub.platform.domain.integration.auth.IntegrationClientProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExecutionEventService {

    private final ExecutionEventRepository repository;
    private final Clock clock;
    private final IntegrationClientProperties properties;

    public ExecutionEvent append(
            TaskExecutionHistory execution,
            ExecutionEventType eventType,
            Long sequence,
            Integer percentage,
            String message,
            Instant clientTimestamp,
            Map<String, Object> details,
            IntegrationActor actor,
            String correlationId
    ) {
        if (eventType == ExecutionEventType.PROGRESS
                && repository.countByExecutionIdAndEventType(
                        execution.getId(), ExecutionEventType.PROGRESS)
                >= properties.getMaxProgressEventsPerExecution()) {
            throw new IntegrationApiException(
                    org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                    "PROGRESS_EVENT_QUOTA_EXCEEDED",
                    "The Execution progress event quota has been reached.",
                    false);
        }
        if (sequence != null && sequence < 1) {
            throw IntegrationApiException.unprocessable(
                    "VALIDATION_FAILED", "Progress sequence must be positive.");
        }
        if (sequence != null
                && repository.countByExecutionIdAndSequenceNumber(execution.getId(), sequence) > 0) {
            throw IntegrationApiException.conflict(
                    "PROGRESS_SEQUENCE_CONFLICT",
                    "The progress sequence has already been recorded.",
                    false);
        }
        if (percentage != null && (percentage < 0 || percentage > 100)) {
            throw IntegrationApiException.unprocessable(
                    "VALIDATION_FAILED", "Progress percentage must be 0-100.");
        }
        if (message != null && message.chars().anyMatch(Character::isISOControl)) {
            throw IntegrationApiException.unprocessable(
                    "VALIDATION_FAILED",
                    "Execution event messages cannot contain control characters.");
        }

        ExecutionEvent event = new ExecutionEvent();
        event.setExecution(execution);
        event.setTask(execution.getTask());
        event.setEventType(eventType);
        event.setSequenceNumber(sequence);
        event.setPercentage(percentage);
        event.setMessage(bound(message, 2000));
        event.setDetails(details == null ? Map.of() : Map.copyOf(details));
        event.setActorKind(ActorKind.HUMAN);
        event.setActorId(actor.principalId());
        event.setClientApplicationId(actor.clientApplicationId());
        event.setCorrelationId(correlationId);
        event.setClientTimestamp(clientTimestamp);
        event.setReceivedAt(clock.instant());
        ExecutionEvent saved = repository.save(event);
        execution.setLastEventAt(clock.instant());
        return saved;
    }

    private static String bound(String value, int maximum) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maximum ? normalized : normalized.substring(0, maximum);
    }
}
