package com.wwa.agenthub.platform.domain.integration;

import com.wwa.agenthub.contracts.dto.integration.IntegrationArtifactDto;
import com.wwa.agenthub.contracts.dto.integration.IntegrationExecutionDto;
import com.wwa.agenthub.contracts.dto.integration.IntegrationReferences;
import com.wwa.agenthub.contracts.dto.integration.IntegrationReviewDto;
import com.wwa.agenthub.contracts.dto.integration.IntegrationTaskDto;
import com.wwa.agenthub.contracts.enums.ArtifactRole;
import com.wwa.agenthub.contracts.enums.ArtifactStorageMode;
import com.wwa.agenthub.contracts.enums.CapabilityType;
import com.wwa.agenthub.contracts.enums.ExecutionStatus;
import com.wwa.agenthub.contracts.enums.IntegrationExecutionStatus;
import com.wwa.agenthub.contracts.enums.TaskStatus;
import com.wwa.agenthub.contracts.validation.IntegrationResourceIds;
import com.wwa.agenthub.domain.releaseflow.ReleaseFlow;
import com.wwa.agenthub.domain.releaseflow.Request;
import com.wwa.agenthub.domain.task.Task;
import com.wwa.agenthub.domain.task.TaskExecutionHistory;
import com.wwa.agenthub.domain.task.TaskExecutionHistoryRepository;
import com.wwa.agenthub.platform.domain.integration.artifact.IntegrationArtifact;
import com.wwa.agenthub.platform.domain.integration.artifact.IntegrationArtifactMetadata;
import com.wwa.agenthub.platform.domain.integration.artifact.TaskInputArtifactApprovalRepository;
import com.wwa.agenthub.platform.domain.integration.auth.IntegrationActor;
import com.wwa.agenthub.platform.domain.integration.auth.IntegrationAuthorizationService;
import com.wwa.agenthub.platform.domain.integration.review.IntegrationReviewDecision;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IntegrationProjectionService {

    private final IntegrationAuthorizationService authorizationService;
    private final TaskExecutionHistoryRepository executionRepository;
    private final TaskInputArtifactApprovalRepository approvalRepository;

    public IntegrationTaskDto toTask(Task task, IntegrationActor actor) {
        Optional<TaskExecutionHistory> latestExecution = executionRepository
                .findFirstByTaskIdAndIntegrationManagedTrueOrderByAttemptNumberDesc(task.getId());
        return toTask(
                task,
                actor,
                latestExecution.orElse(null),
                executionRepository.countByTaskIdAndIntegrationManagedTrue(task.getId()),
                approvalRepository.findArtifactIdsByTaskId(task.getId()));
    }

    public List<IntegrationTaskDto> toTasks(List<Task> tasks, IntegrationActor actor) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        Set<String> taskIds = tasks.stream().map(Task::getId).collect(Collectors.toSet());
        Map<String, TaskExecutionHistory> latestByTask = executionRepository
                .findLatestIntegrationExecutions(taskIds).stream()
                .collect(Collectors.toMap(history -> history.getTask().getId(), Function.identity()));
        Map<String, Long> countsByTask = executionRepository.countIntegrationExecutions(taskIds).stream()
                .collect(Collectors.toMap(
                        TaskExecutionHistoryRepository.IntegrationExecutionCount::getTaskId,
                        TaskExecutionHistoryRepository.IntegrationExecutionCount::getExecutionCount));
        Map<String, List<String>> inputsByTask = approvalRepository.findArtifactIdsByTaskIds(taskIds).stream()
                .collect(Collectors.groupingBy(
                        TaskInputArtifactApprovalRepository.ApprovedArtifactId::getTaskId,
                        Collectors.mapping(
                                TaskInputArtifactApprovalRepository.ApprovedArtifactId::getArtifactId,
                                Collectors.toList())));
        return tasks.stream()
                .map(task -> toTask(
                        task,
                        actor,
                        latestByTask.get(task.getId()),
                        countsByTask.getOrDefault(task.getId(), 0L),
                        inputsByTask.getOrDefault(task.getId(), List.of())))
                .toList();
    }

    private IntegrationTaskDto toTask(
            Task task,
            IntegrationActor actor,
            TaskExecutionHistory latest,
            long executionCount,
            List<String> approvedInputIds
    ) {
        Request request = task.getRequest();
        ReleaseFlow flow = request.getReleaseFlow();
        Optional<TaskExecutionHistory> latestExecution = Optional.ofNullable(latest);
        boolean canStart = canStart(task, actor);
        boolean canReview = !actor.bearerAuthenticated()
                && task.getTaskStatus() == TaskStatus.Awaiting_Review
                && latestExecution.filter(execution -> execution.getExecutionStatus() == ExecutionStatus.Completed)
                        .map(execution -> authorizationService.canReview(execution, actor))
                        .orElse(false);
        boolean canRerun = (task.getTaskStatus() == TaskStatus.Rejected
                || task.getTaskStatus() == TaskStatus.Failed)
                && authorizationService.canRerun(task, actor);
        String latestExecutionId = latestExecution
                .map(TaskExecutionHistory::getId)
                .orElse(null);

        return new IntegrationTaskDto(
                task.getId(),
                flow.getId(),
                request.getAgent(),
                task.getTaskName(),
                task.getTaskGroupName(),
                task.getTaskStatus().name().toUpperCase(),
                // Legacy owner is an imported display-only field and is not a
                // trusted identity label for the Atlas assignee binding.
                user(task.getAssigneeUserId(), task.getAssigneeUserId()),
                capability(task.getCapabilityType(), task.getCapabilityId(), task.getCapabilityVersion()),
                projectContext(
                        flow,
                        request,
                        task.getRepositoryId(),
                        task.getRepositoryUrl(),
                        task.getRepositoryProvider(),
                        task.getRepositoryBranch(),
                        task.getRepositoryCommit()),
                approvedInputIds,
                task.getActiveExecutionId(),
                latestExecutionId,
                Math.toIntExact(executionCount),
                task.getCreatedAt(),
                task.getLastUpdatedAt(),
                new IntegrationReferences.TaskActions(canStart, canReview, canRerun));
    }

    public IntegrationExecutionDto toExecution(TaskExecutionHistory execution) {
        IntegrationReferences.FailureReason failure = execution.getFailureCode() == null
                && execution.getFailureMessage() == null
                ? null
                : new IntegrationReferences.FailureReason(
                        execution.getFailureCode(),
                        execution.getFailureMessage(),
                        Boolean.TRUE.equals(execution.getFailureRetryable()));

        return new IntegrationExecutionDto(
                execution.getId(),
                execution.getTask().getId(),
                execution.getAttemptNumber(),
                user(execution.getUserId(), execution.getUserDisplayName()),
                new IntegrationReferences.Client(
                        execution.getClientApplicationId(),
                        execution.getClientType(),
                        execution.getClientVersion()),
                capability(
                        execution.getCapabilityType(),
                        execution.getCapabilityId(),
                        execution.getCapabilityVersion()),
                executionProjectContext(
                        execution,
                        execution.getRepositoryId(),
                        execution.getRepositoryUrl(),
                        execution.getRepositoryProvider(),
                        execution.getRepositoryBranch(),
                        execution.getRepositoryCommit()),
                execution.getStartTime(),
                execution.getEndTime(),
                publicStatus(execution.getExecutionStatus()),
                execution.getDurationMs(),
                execution.getArtifactCount(),
                failure,
                execution.getCancellationReason(),
                execution.getExecutionStatus() == ExecutionStatus.Running,
                execution.getCorrelationId());
    }

    public IntegrationArtifactDto toArtifact(IntegrationArtifact artifact) {
        return artifact(
                artifact,
                artifact.getTask(),
                artifact.getRole());
    }

    public IntegrationArtifactDto toApprovedInputArtifact(IntegrationArtifact artifact, Task approvedTask) {
        return artifact(artifact, approvedTask, ArtifactRole.INPUT);
    }

    public IntegrationArtifactDto toArtifact(IntegrationArtifactMetadata artifact, Task projectedTask) {
        return artifact(artifact, projectedTask, artifact.role());
    }

    public IntegrationArtifactDto toApprovedInputArtifact(
            IntegrationArtifactMetadata artifact,
            Task approvedTask
    ) {
        return artifact(artifact, approvedTask, ArtifactRole.INPUT);
    }

    public IntegrationReviewDto toReview(IntegrationReviewDecision review) {
        return new IntegrationReviewDto(
                review.getId(),
                review.getTask().getId(),
                review.getExecution().getId(),
                review.getDecision(),
                user(review.getReviewerId(), review.getReviewerDisplayName()),
                review.getComment(),
                review.getDecidedAt(),
                review.getCorrelationId());
    }

    public static IntegrationExecutionStatus publicStatus(ExecutionStatus status) {
        return switch (status) {
            case Running -> IntegrationExecutionStatus.RUNNING;
            case Completed -> IntegrationExecutionStatus.SUCCEEDED;
            case Failed, Timed_Out -> IntegrationExecutionStatus.FAILED;
            case Cancelled -> IntegrationExecutionStatus.CANCELLED;
        };
    }

    private IntegrationArtifactDto artifact(
            IntegrationArtifact artifact,
            Task projectedTask,
            ArtifactRole role
    ) {
        String referenceId = artifact.getStorageMode() == ArtifactStorageMode.REFERENCE
                && artifact.getReferenceArtifact() != null
                ? artifact.getReferenceArtifact().getId()
                : null;
        return new IntegrationArtifactDto(
                artifact.getId(),
                projectedTask.getRequest().getReleaseFlow().getId(),
                projectedTask.getId(),
                artifact.getExecution().getId(),
                role,
                artifact.getKind(),
                artifact.getName(),
                artifact.getMediaType(),
                artifact.getSizeBytes(),
                new IntegrationReferences.Digest("SHA-256", artifact.getSha256()),
                new IntegrationReferences.ArtifactContent(artifact.getStorageMode(), referenceId),
                artifact.getSourcePath(),
                artifact.getCreatedAt());
    }

    private IntegrationArtifactDto artifact(
            IntegrationArtifactMetadata artifact,
            Task projectedTask,
            ArtifactRole role
    ) {
        return new IntegrationArtifactDto(
                artifact.artifactId(),
                projectedTask.getRequest().getReleaseFlow().getId(),
                projectedTask.getId(),
                artifact.executionId(),
                role,
                artifact.kind(),
                artifact.name(),
                artifact.mediaType(),
                artifact.sizeBytes(),
                new IntegrationReferences.Digest("SHA-256", artifact.sha256()),
                new IntegrationReferences.ArtifactContent(
                        artifact.storageMode(), artifact.referenceId()),
                artifact.sourcePath(),
                artifact.createdAt());
    }

    private boolean canStart(Task task, IntegrationActor actor) {
        if (task.getTaskStatus() != TaskStatus.Ready_For_Execution) {
            return false;
        }
        try {
            authorizationService.assertCanStart(task, actor);
            return true;
        } catch (IntegrationApiException exception) {
            return false;
        }
    }

    private static IntegrationReferences.Capability capability(
            CapabilityType type,
            String id,
            String version
    ) {
        return new IntegrationReferences.Capability(
                id,
                type,
                version,
                type == CapabilityType.SKILL ? id : null);
    }

    private static IntegrationReferences.ProjectContext projectContext(
            ReleaseFlow flow,
            Request request,
            String repositoryId,
            String repositoryUrl,
            String repositoryProvider,
            String branch,
            String commit
    ) {
        IntegrationReferences.RepositoryReference repository = repositoryId == null
                && repositoryUrl == null
                && repositoryProvider == null
                ? null
                : new IntegrationReferences.RepositoryReference(
                        repositoryId,
                        repositoryUrl,
                        repositoryProvider);
        return new IntegrationReferences.ProjectContext(
                null,
                new IntegrationReferences.ProjectReference(flow.getProjectId(), flow.getProjectName()),
                repository,
                branch,
                commit,
                request.getSnowGroup(),
                request.getAgent());
    }

    private static IntegrationReferences.ProjectContext executionProjectContext(
            TaskExecutionHistory execution,
            String repositoryId,
            String repositoryUrl,
            String repositoryProvider,
            String branch,
            String commit
    ) {
        IntegrationReferences.RepositoryReference repository = repositoryId == null
                && repositoryUrl == null
                && repositoryProvider == null
                ? null
                : new IntegrationReferences.RepositoryReference(
                        repositoryId,
                        repositoryUrl,
                        repositoryProvider);
        return new IntegrationReferences.ProjectContext(
                null,
                new IntegrationReferences.ProjectReference(
                        execution.getProjectId(), execution.getProjectName()),
                repository,
                branch,
                commit,
                execution.getConfigSnowGroup(),
                execution.getConfigAgent());
    }

    private static IntegrationReferences.User user(String userId, String displayName) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        if (!IntegrationResourceIds.isValid(userId)) {
            throw IntegrationApiException.unprocessable(
                    "INVALID_STORED_IDENTITY",
                    "Stored user identity is incompatible with the Atlas ResourceID contract.");
        }
        String safeDisplayName = displayName == null || displayName.isBlank()
                ? userId
                : displayName;
        if (safeDisplayName.length() > 300
                || safeDisplayName.chars().anyMatch(Character::isISOControl)) {
            safeDisplayName = userId;
        }
        return new IntegrationReferences.User(
                userId,
                safeDisplayName);
    }
}
