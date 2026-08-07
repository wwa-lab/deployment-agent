package com.wwa.agenthub.platform.domain.integration.review;

import com.wwa.agenthub.contracts.dto.integration.IntegrationReviewDto;
import com.wwa.agenthub.contracts.dto.integration.ReviewSubmissionRequest;
import com.wwa.agenthub.contracts.enums.AuditActionType;
import com.wwa.agenthub.contracts.enums.ExecutionEventType;
import com.wwa.agenthub.contracts.enums.ExecutionStatus;
import com.wwa.agenthub.contracts.enums.IntegrationReviewDecisionType;
import com.wwa.agenthub.contracts.enums.TaskStatus;
import com.wwa.agenthub.domain.audit.AuditLoggerService;
import com.wwa.agenthub.domain.decision.DecisionEngine;
import com.wwa.agenthub.domain.decision.DecisionType;
import com.wwa.agenthub.domain.decision.ReleaseFlowProgressionService;
import com.wwa.agenthub.domain.releaseflow.Request;
import com.wwa.agenthub.domain.task.Task;
import com.wwa.agenthub.domain.task.TaskExecutionHistory;
import com.wwa.agenthub.domain.task.TaskExecutionHistoryRepository;
import com.wwa.agenthub.domain.task.TaskRepository;
import com.wwa.agenthub.platform.domain.integration.IntegrationApiException;
import com.wwa.agenthub.platform.domain.integration.IntegrationProjectionService;
import com.wwa.agenthub.platform.domain.integration.SensitiveTextRedactor;
import com.wwa.agenthub.platform.domain.integration.auth.IntegrationActor;
import com.wwa.agenthub.platform.domain.integration.auth.IntegrationAuthorizationService;
import com.wwa.agenthub.platform.domain.integration.event.ExecutionEventService;
import com.wwa.agenthub.platform.web.common.CorrelationIdFilter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class IntegrationReviewService {

    private final IntegrationReviewDecisionRepository reviewRepository;
    private final TaskExecutionHistoryRepository executionRepository;
    private final TaskRepository taskRepository;
    private final IntegrationAuthorizationService authorizationService;
    private final IntegrationProjectionService projectionService;
    private final ExecutionEventService eventService;
    private final DecisionEngine decisionEngine;
    private final ReleaseFlowProgressionService progressionService;
    private final AuditLoggerService auditLogger;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public IntegrationReviewDto get(String executionId, IntegrationActor actor) {
        TaskExecutionHistory execution = executionRepository.findIntegrationExecutionById(executionId)
                .orElseThrow(() -> IntegrationApiException.notFound("EXECUTION_NOT_FOUND", "Execution"));
        authorizationService.assertExecutionVisible(execution, actor);
        IntegrationReviewDecision review = reviewRepository.findByExecutionId(executionId)
                .orElseThrow(() -> IntegrationApiException.notFound(
                        "REVIEW_DECISION_NOT_FOUND", "Review decision"));
        return projectionService.toReview(review);
    }

    @Transactional(readOnly = true)
    public void authorizeSubmit(String executionId, IntegrationActor actor) {
        TaskExecutionHistory execution = executionRepository.findIntegrationExecutionById(executionId)
                .orElseThrow(() -> IntegrationApiException.notFound("EXECUTION_NOT_FOUND", "Execution"));
        authorizationService.assertCanReview(execution, actor);
    }

    @Transactional
    public IntegrationReviewDto submit(
            String executionId,
            ReviewSubmissionRequest request,
            IntegrationActor actor
    ) {
        String comment = safeComment(request.comment());
        TaskExecutionHistory hint = executionRepository.findIntegrationExecutionById(executionId)
                .orElseThrow(() -> IntegrationApiException.notFound("EXECUTION_NOT_FOUND", "Execution"));
        List<Task> lockedTasks = lockRequestTasks(hint.getTask().getRequest().getId());
        Task task = lockedTasks.stream()
                .filter(candidate -> candidate.getId().equals(hint.getTask().getId()))
                .findFirst()
                .orElseThrow(() -> IntegrationApiException.notFound("EXECUTION_NOT_FOUND", "Execution"));
        entityManager.refresh(task.getRequest());
        entityManager.refresh(task.getRequest().getReleaseFlow());
        if (task.getRequest().getArchivedAt() != null
                || task.getRequest().getReleaseFlow().getArchivedAt() != null) {
            throw IntegrationApiException.conflict(
                    "REVIEW_NOT_AVAILABLE",
                    "Archived work cannot be reviewed.",
                    false);
        }
        TaskExecutionHistory execution = executionRepository.findByIdForUpdate(executionId)
                .orElseThrow(() -> IntegrationApiException.notFound("EXECUTION_NOT_FOUND", "Execution"));
        authorizationService.assertCanReview(execution, actor);

        if (execution.getExecutionStatus() != ExecutionStatus.Completed
                || !executionId.equals(task.getLatestExecutionId())
                || task.getActiveExecutionId() != null
                || task.getTaskStatus() != TaskStatus.Awaiting_Review) {
            throw IntegrationApiException.conflict(
                    "REVIEW_NOT_AVAILABLE",
                    "Review is available only for the latest successful Execution.",
                    false);
        }
        if (reviewRepository.findByExecutionId(executionId).isPresent()) {
            throw IntegrationApiException.conflict(
                    "REVIEW_ALREADY_SUBMITTED",
                    "A Review Decision already exists for this Execution.",
                    false);
        }

        IntegrationReviewDecision review = new IntegrationReviewDecision();
        review.setTask(task);
        review.setExecution(execution);
        review.setDecision(request.decision());
        review.setReviewerId(actor.principalId());
        review.setReviewerDisplayName(firstNonBlank(
                actor.user().displayName(), actor.principalId()));
        review.setComment(comment);
        review.setCorrelationId(CorrelationIdFilter.current());
        review = reviewRepository.save(review);

        decisionEngine.applyIntegrationDecision(
                task.getId(),
                decisionType(request.decision()),
                actor.user(),
                review.getComment());
        progressionService.progressAfterDecision(task.getId());

        eventService.append(
                execution,
                ExecutionEventType.REVIEWED,
                null,
                null,
                "Execution reviewed",
                null,
                Map.of("decision", request.decision().name()),
                actor,
                CorrelationIdFilter.current());
        audit(execution, review, actor);
        return projectionService.toReview(review);
    }

    /**
     * A review may release the next Task in the same Request. Lock the entire
     * Request Task set in the same stable ID order used by binding/archive so
     * concurrent control-plane operations cannot form an A-to-B/B-to-A cycle.
     */
    private List<Task> lockRequestTasks(String requestId) {
        List<String> taskIds = taskRepository
                .findByRequestIdOrderByTaskGroupIdAscStepSeqAsc(requestId)
                .stream()
                .map(Task::getId)
                .sorted()
                .toList();
        return taskIds.stream()
                .map(taskId -> taskRepository.findByIdForExecutionUpdate(taskId)
                        .orElseThrow(() -> IntegrationApiException.notFound(
                                "EXECUTION_NOT_FOUND", "Execution")))
                .peek(entityManager::refresh)
                .toList();
    }

    private void audit(
            TaskExecutionHistory execution,
            IntegrationReviewDecision review,
            IntegrationActor actor
    ) {
        Request request = execution.getTask().getRequest();
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("executionId", execution.getId());
        context.put("reviewId", review.getId());
        context.put("decision", review.getDecision().name());
        context.put("application", execution.getConfigApplication());
        context.put("snowGroup", execution.getConfigSnowGroup());
        context.put("agent", execution.getConfigAgent());
        auditLogger.logAtomic(
                actor.user(),
                AuditActionType.integration_review_submit,
                request.getReleaseFlow().getId(),
                request.getId(),
                execution.getTask().getId(),
                context);
    }

    private static DecisionType decisionType(IntegrationReviewDecisionType decision) {
        return switch (decision) {
            case APPROVED -> DecisionType.approve;
            case REJECTED -> DecisionType.reject;
            case SKIPPED -> DecisionType.skip;
        };
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String safeComment(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() > 2000
                || !SensitiveTextRedactor.isSafeEvidenceText(normalized)) {
            throw IntegrationApiException.unprocessable(
                    "VALIDATION_FAILED",
                    "Review comment must be bounded safe prose without secrets, source, configuration, or raw logs.");
        }
        return normalized;
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred.trim();
    }
}
