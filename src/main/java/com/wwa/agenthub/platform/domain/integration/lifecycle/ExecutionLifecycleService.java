package com.wwa.agenthub.platform.domain.integration.lifecycle;

import com.wwa.agenthub.contracts.dto.integration.CancelExecutionRequest;
import com.wwa.agenthub.contracts.dto.integration.FailExecutionRequest;
import com.wwa.agenthub.contracts.dto.integration.IntegrationExecutionDto;
import com.wwa.agenthub.contracts.dto.integration.IntegrationProgressEventDto;
import com.wwa.agenthub.contracts.dto.integration.IntegrationTaskDto;
import com.wwa.agenthub.contracts.dto.integration.ProgressEventRequest;
import com.wwa.agenthub.contracts.dto.integration.RerunTaskRequest;
import com.wwa.agenthub.contracts.dto.integration.StartExecutionRequest;
import com.wwa.agenthub.contracts.dto.integration.SubmitExecutionRequest;
import com.wwa.agenthub.contracts.enums.ActorKind;
import com.wwa.agenthub.contracts.enums.AuditActionType;
import com.wwa.agenthub.contracts.enums.CapabilityType;
import com.wwa.agenthub.contracts.enums.ExecutionEventType;
import com.wwa.agenthub.contracts.enums.ExecutionStatus;
import com.wwa.agenthub.contracts.enums.TaskStatus;
import com.wwa.agenthub.domain.audit.AuditLoggerService;
import com.wwa.agenthub.domain.releaseflow.ReleaseFlow;
import com.wwa.agenthub.domain.releaseflow.ReleaseFlowService;
import com.wwa.agenthub.domain.releaseflow.Request;
import com.wwa.agenthub.domain.task.Task;
import com.wwa.agenthub.domain.task.TaskExecutionHistory;
import com.wwa.agenthub.domain.task.TaskExecutionHistoryRepository;
import com.wwa.agenthub.domain.task.TaskRepository;
import com.wwa.agenthub.domain.task.TaskStateMachine;
import com.wwa.agenthub.platform.domain.integration.IntegrationApiException;
import com.wwa.agenthub.platform.domain.integration.IntegrationProjectionService;
import com.wwa.agenthub.platform.domain.integration.SensitiveTextRedactor;
import com.wwa.agenthub.platform.domain.integration.artifact.IntegrationArtifactRepository;
import com.wwa.agenthub.platform.domain.integration.artifact.IntegrationArtifact;
import com.wwa.agenthub.contracts.enums.ArtifactStorageMode;
import com.wwa.agenthub.platform.domain.integration.auth.IntegrationActor;
import com.wwa.agenthub.platform.domain.integration.auth.IntegrationAuthorizationService;
import com.wwa.agenthub.platform.domain.integration.auth.IntegrationClientProperties;
import com.wwa.agenthub.platform.domain.integration.auth.PresentedCredentialLeakGuard;
import com.wwa.agenthub.platform.domain.integration.event.ExecutionEventService;
import com.wwa.agenthub.platform.domain.integration.event.ExecutionEvent;
import com.wwa.agenthub.platform.web.common.CorrelationIdFilter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ExecutionLifecycleService {

    private static final Pattern RESOURCE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
    private static final int READ_BATCH_SIZE = 200;
    private static final String SUBMISSION_SUMMARY = "Execution submitted.";

    private final TaskRepository taskRepository;
    private final IntegrationTaskQueryRepository taskQueryRepository;
    private final TaskExecutionHistoryRepository executionRepository;
    private final IntegrationArtifactRepository artifactRepository;
    private final IntegrationAuthorizationService authorizationService;
    private final IntegrationProjectionService projectionService;
    private final ExecutionEventService eventService;
    private final AuditLoggerService auditLogger;
    private final ReleaseFlowService releaseFlowService;
    private final IntegrationClientProperties properties;
    private final PresentedCredentialLeakGuard credentialLeakGuard;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public TaskWindow listTasks(
            IntegrationActor actor,
            TaskFilters filters,
            TaskCursor cursor,
            int limit
    ) {
        validateTaskFilters(filters);
        List<Task> candidates = taskQueryRepository.find(actor, filters, cursor, limit + 1);
        boolean hasMore = candidates.size() > limit;
        List<Task> scanned = candidates.stream().limit(limit).toList();
        List<Task> visible = scanned.stream()
                .filter(task -> authorizationService.isVisibleForList(task, actor))
                .toList();
        TaskCursor nextCursor = scanned.isEmpty()
                ? null
                : new TaskCursor(
                        scanned.get(scanned.size() - 1).getCreatedAt(),
                        scanned.get(scanned.size() - 1).getId());
        return new TaskWindow(
                projectionService.toTasks(visible, actor),
                hasMore,
                nextCursor);
    }

    @Transactional(readOnly = true)
    public IntegrationTaskDto getTask(String taskId, IntegrationActor actor) {
        Task task = taskRepository.findIntegrationTaskById(taskId)
                .orElseThrow(() -> IntegrationApiException.notFound("TASK_NOT_FOUND", "Task"));
        authorizationService.assertTaskVisible(task, actor);
        return projectionService.toTask(task, actor);
    }

    @Transactional(readOnly = true)
    public IntegrationExecutionDto getExecution(String executionId, IntegrationActor actor) {
        TaskExecutionHistory execution = executionRepository.findIntegrationExecutionById(executionId)
                .orElseThrow(() -> IntegrationApiException.notFound("EXECUTION_NOT_FOUND", "Execution"));
        authorizationService.assertExecutionVisible(execution, actor);
        return projectionService.toExecution(execution);
    }

    @Transactional(readOnly = true)
    public void authorizeStart(String taskId, IntegrationActor actor) {
        Task task = taskRepository.findIntegrationTaskById(taskId)
                .orElseThrow(() -> IntegrationApiException.notFound("TASK_NOT_FOUND", "Task"));
        authorizationService.assertCanStart(task, actor);
    }

    @Transactional(readOnly = true)
    public void authorizeWrite(String executionId, IntegrationActor actor) {
        TaskExecutionHistory execution = executionRepository.findIntegrationExecutionById(executionId)
                .orElseThrow(() -> IntegrationApiException.notFound("EXECUTION_NOT_FOUND", "Execution"));
        authorizationService.assertCanWriteExecution(execution, actor);
        if (!executionId.equals(execution.getTask().getLatestExecutionId())) {
            throw IntegrationApiException.conflict(
                    "STALE_EXECUTION",
                    "The Execution is no longer the latest attempt.",
                    false);
        }
    }

    @Transactional
    public void authorizeStartReplay(String taskId, IntegrationActor actor) {
        Task task = taskRepository.findByIdForExecutionUpdate(taskId)
                .orElseThrow(() -> IntegrationApiException.notFound("TASK_NOT_FOUND", "Task"));
        refreshRequest(task);
        authorizationService.assertCanStart(task, actor);
    }

    @Transactional
    public void authorizeWriteReplay(String executionId, IntegrationActor actor) {
        ReplayFence fence = lockReplayFence(executionId);
        authorizationService.assertCanWriteExecution(fence.execution(), actor);
        assertLatestReplay(fence.task(), executionId);
    }

    @Transactional
    public void authorizeReviewReplay(String executionId, IntegrationActor actor) {
        ReplayFence fence = lockReplayFence(executionId);
        authorizationService.assertCanReview(fence.execution(), actor);
        assertLatestReplay(fence.task(), executionId);
    }

    @Transactional
    public void authorizeRerunReplay(
            String taskId,
            String executionId,
            IntegrationActor actor
    ) {
        Task task = taskRepository.findByIdForExecutionUpdate(taskId)
                .orElseThrow(() -> IntegrationApiException.notFound("TASK_NOT_FOUND", "Task"));
        refreshRequest(task);
        authorizationService.assertCanRerun(task, actor);
        assertLatestReplay(task, executionId);
    }

    @Transactional(readOnly = true)
    public ExecutionWindow history(
            String taskId,
            IntegrationActor actor,
            ExecutionCursor cursor,
            int limit
    ) {
        Task task = taskRepository.findIntegrationTaskById(taskId)
                .orElseThrow(() -> IntegrationApiException.notFound("TASK_NOT_FOUND", "Task"));
        authorizationService.assertTaskVisible(task, actor);
        List<IntegrationExecutionDto> results = new java.util.ArrayList<>(limit);
        ExecutionCursor scanCursor = cursor;
        ExecutionCursor continuation = cursor;
        while (true) {
            List<TaskExecutionHistory> candidates = executionRepository
                    .findIntegrationHistoryAfter(
                            taskId,
                            scanCursor == null ? null : scanCursor.attemptNumber(),
                            scanCursor == null ? null : scanCursor.executionId(),
                            org.springframework.data.domain.PageRequest.of(0, READ_BATCH_SIZE));
            for (TaskExecutionHistory execution : candidates) {
                scanCursor = new ExecutionCursor(execution.getAttemptNumber(), execution.getId());
                if (!authorizationService.isExecutionVisibleForList(execution, actor)) {
                    continuation = scanCursor;
                    continue;
                }
                if (results.size() == limit) {
                    return new ExecutionWindow(results, true, continuation);
                }
                results.add(projectionService.toExecution(execution));
                continuation = scanCursor;
            }
            if (candidates.size() < READ_BATCH_SIZE) {
                return new ExecutionWindow(results, false, null);
            }
        }
    }

    private ReplayFence lockReplayFence(String executionId) {
        Task task = taskRepository.findByIntegrationExecutionIdForUpdate(executionId)
                .orElseThrow(() -> IntegrationApiException.notFound("EXECUTION_NOT_FOUND", "Execution"));
        refreshRequest(task);
        TaskExecutionHistory execution = executionRepository.findIntegrationExecutionById(executionId)
                .orElseThrow(() -> IntegrationApiException.notFound("EXECUTION_NOT_FOUND", "Execution"));
        return new ReplayFence(task, execution);
    }

    private static void assertLatestReplay(Task task, String executionId) {
        if (!Objects.equals(task.getLatestExecutionId(), executionId)) {
            throw IntegrationApiException.conflict(
                    "STALE_EXECUTION",
                    "The Execution is no longer the latest attempt.",
                    false);
        }
    }

    private static boolean matches(Task task, TaskFilters filters) {
        if (filters == null) {
            return true;
        }
        Request request = task.getRequest();
        return matches(filters.status(), task.getTaskStatus().name().toUpperCase())
                && matches(filters.projectId(), request.getReleaseFlow().getProjectId())
                && matches(filters.team(), request.getSnowGroup())
                && matches(filters.agentModuleId(), request.getAgent());
    }

    private static void validateTaskFilters(TaskFilters filters) {
        if (filters == null || filters.status() == null || filters.status().isBlank()) {
            return;
        }
        String requested = filters.status().trim();
        boolean supported = java.util.Arrays.stream(TaskStatus.values())
                .map(status -> status.name().toUpperCase())
                .anyMatch(status -> status.equalsIgnoreCase(requested));
        if (!supported) {
            throw IntegrationApiException.badRequest(
                    "INVALID_REQUEST",
                    "Task status filter is invalid.");
        }
    }

    private static boolean matches(String expected, String actual) {
        return expected == null || expected.isBlank()
                || (actual != null && expected.trim().equalsIgnoreCase(actual));
    }

    @Transactional
    public IntegrationExecutionDto start(
            String taskId,
            StartExecutionRequest command,
            IntegrationActor actor
    ) {
        Task task = taskRepository.findByIdForExecutionUpdate(taskId)
                .orElseThrow(() -> IntegrationApiException.notFound("TASK_NOT_FOUND", "Task"));
        refreshRequest(task);
        authorizationService.assertCanStart(task, actor);
        validateStartAssertions(task, command);
        assertActiveRequest(task);
        if (task.getActiveExecutionId() != null) {
            throw IntegrationApiException.conflict(
                    "ACTIVE_EXECUTION_EXISTS",
                    "The Task already has an active Execution.",
                    false);
        }
        if (task.getTaskStatus() != TaskStatus.Ready_For_Execution) {
            throw IntegrationApiException.conflict(
                    "TASK_NOT_EXECUTABLE",
                    "The Task is not ready for execution.",
                    false);
        }
        assertAttemptQuota(taskId);

        Request request = task.getRequest();
        ReleaseFlow flow = request.getReleaseFlow();
        TaskExecutionHistory execution = new TaskExecutionHistory();
        execution.setTask(task);
        execution.setAttemptNumber(executionRepository.findMaxAttemptNumberByTaskId(taskId) + 1);
        execution.setExecutionStatus(ExecutionStatus.Running);
        // Integration clients consume only explicitly approved input Artifacts.
        // Do not duplicate legacy script/input maps into this Execution record.
        execution.setInputSnapshot(null);
        execution.setStartTime(clock.instant());
        execution.setActorKind(ActorKind.HUMAN);
        execution.setActorRef(actor.clientApplicationId());
        execution.setIntegrationManaged(true);
        execution.setUserId(actor.principalId());
        execution.setUserDisplayName(firstNonBlank(actor.user().displayName(), actor.principalId()));
        execution.setClientApplicationId(actor.clientApplicationId());
        execution.setClientType(actor.clientType());
        execution.setClientVersion(command.clientVersion().trim());
        execution.setCapabilityType(task.getCapabilityType());
        execution.setCapabilityId(task.getCapabilityId());
        execution.setCapabilityVersion(task.getCapabilityVersion());
        execution.setProjectId(flow.getProjectId());
        execution.setProjectName(flow.getProjectName());
        execution.setConfigApplication(request.getApplication());
        execution.setConfigSnowGroup(request.getSnowGroup());
        execution.setConfigAgent(request.getAgent());
        execution.setRepositoryId(task.getRepositoryId());
        execution.setRepositoryProvider(task.getRepositoryProvider());
        execution.setRepositoryUrl(task.getRepositoryUrl());
        execution.setRepositoryBranch(normalize(command.projectContext().branch()));
        execution.setRepositoryCommit(normalize(command.projectContext().commit()));
        execution.setArtifactCount(0);
        execution.setCorrelationId(CorrelationIdFilter.current());
        execution = executionRepository.save(execution);

        transition(task, TaskStatus.Executing);
        task.setActiveExecutionId(execution.getId());
        task.setLatestExecutionId(execution.getId());
        task.setStartTime(execution.getStartTime());
        task.setEndTime(null);

        eventService.append(
                execution,
                ExecutionEventType.STARTED,
                null,
                0,
                "Execution started",
                null,
                Map.of("attemptNumber", execution.getAttemptNumber()),
                actor,
                CorrelationIdFilter.current());
        audit(execution, actor, AuditActionType.integration_execution_start,
                Map.of("attemptNumber", execution.getAttemptNumber(), "clientType", actor.clientType().name()));
        recomputeParentStatus(task);
        return projectionService.toExecution(execution);
    }

    @Transactional
    public IntegrationTaskDto rerun(
            String taskId,
            RerunTaskRequest command,
            IntegrationActor actor
    ) {
        Task task = taskRepository.findByIdForExecutionUpdate(taskId)
                .orElseThrow(() -> IntegrationApiException.notFound("TASK_NOT_FOUND", "Task"));
        refreshRequest(task);
        authorizationService.assertCanRerun(task, actor);
        assertActiveRequest(task);
        if (task.getActiveExecutionId() != null
                || !Objects.equals(task.getLatestExecutionId(), command.executionId())
                || (task.getTaskStatus() != TaskStatus.Failed
                && task.getTaskStatus() != TaskStatus.Rejected)) {
            throw IntegrationApiException.conflict(
                    "RERUN_NOT_AVAILABLE",
                    "Only the exact latest Failed or Rejected Task attempt can be rerun.",
                    false);
        }
        assertAttemptQuota(taskId);
        TaskExecutionHistory execution = executionRepository.findByIdForUpdate(command.executionId())
                .filter(candidate -> candidate.isIntegrationManaged()
                        && Objects.equals(candidate.getTask().getId(), taskId))
                .orElseThrow(() -> IntegrationApiException.notFound("EXECUTION_NOT_FOUND", "Execution"));
        authorizationService.assertExecutionVisible(execution, actor);
        TaskStatus previous = task.getTaskStatus();
        transition(task, TaskStatus.Ready_For_Execution);
        task.setStartTime(null);
        task.setEndTime(null);
        eventService.append(
                execution,
                ExecutionEventType.RERUN_REQUESTED,
                null,
                null,
                "Task rerun requested",
                null,
                Map.of("previousTaskStatus", previous.name()),
                actor,
                CorrelationIdFilter.current());
        audit(execution, actor, AuditActionType.integration_task_rerun,
                Map.of("previousTaskStatus", previous.name()));
        recomputeParentStatus(task);
        return projectionService.toTask(task, actor);
    }

    @Transactional(readOnly = true)
    public void authorizeRerun(String taskId, IntegrationActor actor) {
        Task task = taskRepository.findIntegrationTaskById(taskId)
                .orElseThrow(() -> IntegrationApiException.notFound("TASK_NOT_FOUND", "Task"));
        authorizationService.assertCanRerun(task, actor);
    }

    @Transactional
    public IntegrationProgressEventDto progress(
            String executionId,
            ProgressEventRequest command,
            IntegrationActor actor
    ) {
        ActiveExecution active = lockActive(executionId, actor);
        String message = requireSafeOperationalText(command.message(), "Progress message", 2000);
        ExecutionEvent event = eventService.append(
                active.execution(),
                ExecutionEventType.PROGRESS,
                command.sequenceNumber(),
                command.percent(),
                message,
                command.clientTimestamp(),
                Map.of(),
                actor,
                CorrelationIdFilter.current());
        audit(active.execution(), actor, AuditActionType.integration_execution_progress,
                command.percent() == null ? Map.of() : Map.of("percent", command.percent()));
        return new IntegrationProgressEventDto(
                event.getId(),
                active.execution().getId(),
                event.getSequenceNumber(),
                event.getPercentage(),
                event.getMessage(),
                event.getClientTimestamp(),
                event.getReceivedAt());
    }

    @Transactional
    public IntegrationExecutionDto submit(
            String executionId,
            SubmitExecutionRequest command,
            IntegrationActor actor
    ) {
        ActiveExecution active = lockActive(executionId, actor);
        TaskExecutionHistory execution = active.execution();
        String summary = optionalSafeOperationalText(command.summary(), "Submission summary", 10000);
        validateDeclaredArtifacts(executionId, command.artifactIds());
        long artifactCount = artifactRepository.countByExecutionId(executionId);
        if (execution.getCapabilityType() != CapabilityType.MANUAL && artifactCount == 0) {
            throw IntegrationApiException.conflict(
                    "REQUIRED_ARTIFACTS_MISSING",
                    "A non-manual Execution requires at least one output or evidence Artifact.",
                    false);
        }

        execution.setExecutionStatus(ExecutionStatus.Completed);
        execution.setResultSummary(Map.of(
                "summary",
                summary == null ? SUBMISSION_SUMMARY : summary));
        complete(active.task(), execution, TaskStatus.Awaiting_Review, artifactCount);
        eventService.append(
                execution,
                ExecutionEventType.SUBMITTED,
                null,
                100,
                "Execution submitted",
                null,
                Map.of("artifactCount", artifactCount),
                actor,
                CorrelationIdFilter.current());
        audit(execution, actor, AuditActionType.integration_execution_submit,
                Map.of("artifactCount", artifactCount));
        recomputeParentStatus(active.task());
        return projectionService.toExecution(execution);
    }

    @Transactional
    public IntegrationExecutionDto fail(
            String executionId,
            FailExecutionRequest command,
            IntegrationActor actor
    ) {
        ActiveExecution active = lockActive(executionId, actor);
        TaskExecutionHistory execution = active.execution();
        if (!SensitiveTextRedactor.redact(command.failureReason().code())
                .equals(command.failureReason().code())
                || credentialLeakGuard.contains(command.failureReason().code())) {
            throw IntegrationApiException.unprocessable(
                    "VALIDATION_FAILED",
                    "Failure code contains prohibited secret material.");
        }
        String failureMessage = requireSafeOperationalText(
                command.failureReason().message(),
                "Failure message",
                2000);
        execution.setExecutionStatus(ExecutionStatus.Failed);
        execution.setFailureCode(command.failureReason().code());
        execution.setFailureMessage(failureMessage);
        execution.setFailureRetryable(command.failureReason().retryable());
        long artifactCount = artifactRepository.countByExecutionId(executionId);
        complete(active.task(), execution, TaskStatus.Failed, artifactCount);
        eventService.append(
                execution,
                ExecutionEventType.FAILED,
                null,
                null,
                "Execution failed",
                null,
                Map.of(
                        "failureCode", command.failureReason().code(),
                        "retryable", command.failureReason().retryable()),
                actor,
                CorrelationIdFilter.current());
        audit(execution, actor, AuditActionType.integration_execution_fail,
                Map.of(
                        "failureCode", command.failureReason().code(),
                        "retryable", command.failureReason().retryable()));
        recomputeParentStatus(active.task());
        return projectionService.toExecution(execution);
    }

    @Transactional
    public IntegrationExecutionDto cancel(
            String executionId,
            CancelExecutionRequest command,
            IntegrationActor actor
    ) {
        ActiveExecution active = lockActive(executionId, actor);
        TaskExecutionHistory execution = active.execution();
        String cancellationReason = requireSafeOperationalText(
                command.reason(),
                "Cancellation reason",
                2000);
        execution.setExecutionStatus(ExecutionStatus.Cancelled);
        execution.setCancellationReason(cancellationReason);
        long artifactCount = artifactRepository.countByExecutionId(executionId);
        complete(active.task(), execution, TaskStatus.Ready_For_Execution, artifactCount);
        eventService.append(
                execution,
                ExecutionEventType.CANCELLED,
                null,
                null,
                "Execution cancelled",
                null,
                Map.of(),
                actor,
                CorrelationIdFilter.current());
        audit(execution, actor, AuditActionType.integration_execution_cancel, Map.of());
        recomputeParentStatus(active.task());
        return projectionService.toExecution(execution);
    }

    /** Used by Artifact/Review services to enforce the exact same lock order and fence. */
    @Transactional
    public ActiveExecution lockActive(String executionId, IntegrationActor actor) {
        TaskExecutionHistory hint = executionRepository.findIntegrationExecutionById(executionId)
                .orElseThrow(() -> IntegrationApiException.notFound("EXECUTION_NOT_FOUND", "Execution"));
        Task task = taskRepository.findByIdForExecutionUpdate(hint.getTask().getId())
                .orElseThrow(() -> IntegrationApiException.notFound("EXECUTION_NOT_FOUND", "Execution"));
        refreshRequest(task);
        assertActiveRequest(task);
        TaskExecutionHistory execution = executionRepository.findByIdForUpdate(executionId)
                .orElseThrow(() -> IntegrationApiException.notFound("EXECUTION_NOT_FOUND", "Execution"));
        authorizationService.assertCanWriteExecution(execution, actor);
        if (execution.getExecutionStatus() != ExecutionStatus.Running
                && executionId.equals(task.getLatestExecutionId())) {
            throw IntegrationApiException.conflict(
                    "EXECUTION_ALREADY_FINALIZED",
                    "The Execution is already terminal.",
                    false);
        }
        if (!executionId.equals(task.getActiveExecutionId())
                || !executionId.equals(task.getLatestExecutionId())
                || task.getTaskStatus() != TaskStatus.Executing
                || execution.getExecutionStatus() != ExecutionStatus.Running) {
            throw IntegrationApiException.conflict(
                    "STALE_EXECUTION",
                    "The Execution is no longer the active attempt.",
                    false);
        }
        return new ActiveExecution(task, execution);
    }

    private void complete(
            Task task,
            TaskExecutionHistory execution,
            TaskStatus targetTaskStatus,
            long artifactCount
    ) {
        Instant endedAt = clock.instant();
        execution.setEndTime(endedAt);
        execution.setDurationMs(Math.max(0, Duration.between(execution.getStartTime(), endedAt).toMillis()));
        execution.setArtifactCount(Math.toIntExact(artifactCount));
        transition(task, targetTaskStatus);
        task.setActiveExecutionId(null);
        task.setEndTime(endedAt);
        task.setCurrentResultSummary(execution.getResultSummary());
    }

    private void validateDeclaredArtifacts(String executionId, List<String> artifactIds) {
        Set<String> unique = new HashSet<>();
        for (String artifactId : artifactIds) {
            if (artifactId == null
                    || !RESOURCE_ID.matcher(artifactId).matches()
                    || !unique.add(artifactId)) {
                throw IntegrationApiException.unprocessable(
                        "VALIDATION_FAILED",
                        "Artifact identifiers must be unique and belong to this Execution.");
            }
        }
        List<String> localIds = artifactRepository.findArtifactIdsForSubmission(executionId);
        List<String> sourceIds = artifactRepository.findReferencedArtifactIdsForSubmission(executionId);
        List<String> lockIds = java.util.stream.Stream.concat(localIds.stream(), sourceIds.stream())
                .distinct()
                .sorted()
                .toList();
        Map<String, IntegrationArtifact> locked = new LinkedHashMap<>();
        for (String lockId : lockIds) {
            IntegrationArtifact artifact = artifactRepository.findByIdForSubmissionUpdate(lockId)
                    .orElseThrow(() -> IntegrationApiException.conflict(
                            "ARTIFACT_CONTENT_NOT_AVAILABLE",
                            "Every submitted Artifact must still resolve to durable content.",
                            false));
            locked.put(lockId, artifact);
        }
        List<IntegrationArtifact> persisted = localIds.stream()
                .map(locked::get)
                .toList();
        Set<String> persistedIds = Set.copyOf(localIds);
        if (!unique.equals(persistedIds)) {
            throw IntegrationApiException.unprocessable(
                    "VALIDATION_FAILED",
                    "artifactIds must declare every persisted Artifact for this Execution.");
        }
        Instant renewedExpiry = clock.instant().plus(properties.getArtifactContentRetention());
        for (IntegrationArtifact artifact : persisted) {
            IntegrationArtifact source = artifact.getStorageMode() == ArtifactStorageMode.REFERENCE
                    ? locked.get(artifact.getReferenceArtifact().getId())
                    : artifact;
            if (source == null || source.getContent() == null || source.getContentPurgedAt() != null) {
                throw IntegrationApiException.conflict(
                        "ARTIFACT_CONTENT_NOT_AVAILABLE",
                        "Every submitted Artifact must still resolve to durable content.",
                        false);
            }
            if (!source.isLegalHold()
                    && (source.getContentExpiresAt() == null
                    || source.getContentExpiresAt().isBefore(renewedExpiry))) {
                source.setContentExpiresAt(renewedExpiry);
            }
        }
    }

    private void validateStartAssertions(Task task, StartExecutionRequest command) {
        if (command == null || command.capability() == null || command.projectContext() == null) {
            throw IntegrationApiException.badRequest(
                    "INVALID_REQUEST",
                    "Client version, Capability, and project context are required.");
        }
        String clientVersion = normalize(command.clientVersion());
        if (clientVersion == null
                || clientVersion.length() > 128
                || clientVersion.chars().anyMatch(Character::isISOControl)
                || !SensitiveTextRedactor.redact(clientVersion).equals(clientVersion)
                || credentialLeakGuard.contains(clientVersion)) {
            throw IntegrationApiException.badRequest(
                    "INVALID_REQUEST",
                    "Client version is missing or invalid.");
        }
        StartExecutionRequest.Capability capability = command.capability();
        if (capability.capabilityType() != task.getCapabilityType()
                || !Objects.equals(normalize(capability.capabilityId()), task.getCapabilityId())
                || !Objects.equals(normalize(capability.capabilityVersion()), task.getCapabilityVersion())) {
            throw IntegrationApiException.conflict(
                    "CAPABILITY_MISMATCH",
                    "The asserted Capability does not match the Atlas Task.",
                    false);
        }

        StartExecutionRequest.ProjectContext context = command.projectContext();
        String expectedProject = task.getRequest().getReleaseFlow().getProjectId();
        boolean contextMatches = Objects.equals(normalize(context.projectId()), expectedProject)
                && Objects.equals(normalize(context.repositoryId()), normalize(task.getRepositoryId()))
                && Objects.equals(normalize(context.branch()), normalize(task.getRepositoryBranch()))
                && Objects.equals(normalize(context.commit()), normalize(task.getRepositoryCommit()));
        if (!contextMatches) {
            throw IntegrationApiException.conflict(
                    "PROJECT_CONTEXT_MISMATCH",
                    "The asserted project or repository context does not match Atlas.",
                    false);
        }
    }

    private static void transition(Task task, TaskStatus target) {
        if (!TaskStateMachine.isValid(task.getTaskStatus(), target)) {
            throw IntegrationApiException.conflict(
                    "INVALID_STATE_TRANSITION",
                    "The requested command is not legal from the current Task state.",
                    false);
        }
        task.setTaskStatus(target);
    }

    private void assertAttemptQuota(String taskId) {
        if (executionRepository.countByTaskIdAndIntegrationManagedTrue(taskId)
                >= properties.getMaxExecutionsPerTask()) {
            throw IntegrationApiException.conflict(
                    "EXECUTION_ATTEMPT_QUOTA_EXCEEDED",
                    "The Task Execution attempt quota has been reached.",
                    false);
        }
    }

    private void recomputeParentStatus(Task task) {
        releaseFlowService.recomputeAndPersistStatus(task.getRequest().getReleaseFlow().getId());
    }

    private static void assertActiveRequest(Task task) {
        if (task.getRequest().getArchivedAt() != null
                || task.getRequest().getReleaseFlow().getArchivedAt() != null) {
            throw IntegrationApiException.conflict(
                    "TASK_NOT_EXECUTABLE",
                    "Archived work is read-only.",
                    false);
        }
    }

    private void refreshRequest(Task task) {
        entityManager.refresh(task.getRequest());
        entityManager.refresh(task.getRequest().getReleaseFlow());
    }

    private void audit(
            TaskExecutionHistory execution,
            IntegrationActor actor,
            AuditActionType action,
            Map<String, Object> details
    ) {
        Request request = execution.getTask().getRequest();
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("executionId", execution.getId());
        context.put("attemptNumber", execution.getAttemptNumber());
        context.put("capabilityId", execution.getCapabilityId());
        context.put("clientApplicationId", actor.clientApplicationId());
        context.put("application", execution.getConfigApplication());
        context.put("snowGroup", execution.getConfigSnowGroup());
        context.put("agent", execution.getConfigAgent());
        context.putAll(details);
        auditLogger.logAtomic(
                actor.user(),
                action,
                request.getReleaseFlow().getId(),
                request.getId(),
                execution.getTask().getId(),
                context);
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred.trim();
    }

    private String requireSafeOperationalText(
            String value,
            String field,
            int maximumLength
    ) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw IntegrationApiException.unprocessable(
                    "VALIDATION_FAILED",
                    field + " is required.");
        }
        return safeOperationalText(normalized, field, maximumLength);
    }

    private String optionalSafeOperationalText(
            String value,
            String field,
            int maximumLength
    ) {
        String normalized = normalize(value);
        return normalized == null ? null : safeOperationalText(normalized, field, maximumLength);
    }

    private String safeOperationalText(
            String normalized,
            String field,
            int maximumLength
    ) {
        if (normalized.length() > maximumLength
                || !SensitiveTextRedactor.isSafeEvidenceText(normalized)
                || credentialLeakGuard.contains(normalized)) {
            throw IntegrationApiException.unprocessable(
                    "VALIDATION_FAILED",
                    field + " must be bounded safe prose without secrets, source, configuration, or raw logs.");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record ActiveExecution(Task task, TaskExecutionHistory execution) {
    }

    private record ReplayFence(Task task, TaskExecutionHistory execution) {
    }

    public record TaskFilters(String status, String projectId, String team, String agentModuleId) {
    }

    public record TaskCursor(Instant createdAt, String taskId) {
        public TaskCursor {
            Objects.requireNonNull(createdAt, "createdAt");
            if (taskId == null || taskId.isBlank()) {
                throw new IllegalArgumentException("taskId is required");
            }
        }
    }

    public record TaskWindow(List<IntegrationTaskDto> items, boolean hasMore, TaskCursor nextCursor) {
        public TaskWindow {
            items = items == null ? List.of() : List.copyOf(items);
            if (hasMore && nextCursor == null) {
                throw new IllegalArgumentException("A continuing Task page requires a cursor");
            }
        }
    }

    public record ExecutionCursor(int attemptNumber, String executionId) {
        public ExecutionCursor {
            if (attemptNumber < 1 || executionId == null || executionId.isBlank()) {
                throw new IllegalArgumentException("Execution cursor is invalid");
            }
        }
    }

    public record ExecutionWindow(
            List<IntegrationExecutionDto> items,
            boolean hasMore,
            ExecutionCursor nextCursor
    ) {
        public ExecutionWindow {
            items = items == null ? List.of() : List.copyOf(items);
            if (hasMore && nextCursor == null) {
                throw new IllegalArgumentException("A continuing Execution page requires a cursor");
            }
        }
    }
}
