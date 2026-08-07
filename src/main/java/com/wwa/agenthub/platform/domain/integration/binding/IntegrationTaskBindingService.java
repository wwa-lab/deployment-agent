package com.wwa.agenthub.platform.domain.integration.binding;

import com.wwa.agenthub.contracts.dto.integration.IntegrationTaskBindingRequest;
import com.wwa.agenthub.contracts.dto.integration.IntegrationTaskDto;
import com.wwa.agenthub.contracts.enums.AuditActionType;
import com.wwa.agenthub.contracts.enums.PermissionKey;
import com.wwa.agenthub.contracts.enums.TaskStatus;
import com.wwa.agenthub.domain.audit.AuditLoggerService;
import com.wwa.agenthub.domain.releaseflow.Request;
import com.wwa.agenthub.domain.task.Task;
import com.wwa.agenthub.domain.task.TaskExecutionHistoryRepository;
import com.wwa.agenthub.domain.task.TaskRepository;
import com.wwa.agenthub.platform.domain.StagePipelineRegistry;
import com.wwa.agenthub.platform.domain.integration.IntegrationApiException;
import com.wwa.agenthub.platform.domain.integration.IntegrationProjectionService;
import com.wwa.agenthub.platform.domain.integration.SensitiveTextRedactor;
import com.wwa.agenthub.platform.domain.integration.auth.IntegrationActor;
import com.wwa.agenthub.platform.domain.integration.auth.IntegrationAuthorizationService;
import com.wwa.agenthub.platform.web.common.CorrelationIdFilter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Platform control-plane provisioning for turning an imported Task into CLI work. */
@Service
@RequiredArgsConstructor
public class IntegrationTaskBindingService {

    private final TaskRepository taskRepository;
    private final TaskExecutionHistoryRepository executionRepository;
    private final StagePipelineRegistry stagePipelineRegistry;
    private final IntegrationAuthorizationService authorizationService;
    private final IntegrationProjectionService projectionService;
    private final AuditLoggerService auditLogger;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public void authorize(String taskId, IntegrationActor actor) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> IntegrationApiException.notFound("TASK_NOT_FOUND", "Task"));
        assertAuthorized(task, actor);
    }

    @Transactional
    public IntegrationTaskDto bind(
            String taskId,
            IntegrationTaskBindingRequest command,
            IntegrationActor actor
    ) {
        Task hint = taskRepository.findById(taskId)
                .orElseThrow(() -> IntegrationApiException.notFound("TASK_NOT_FOUND", "Task"));
        List<Task> lockedTasks = lockRequestTasks(hint.getRequest().getId());
        Task task = lockedTasks.stream()
                .filter(candidate -> candidate.getId().equals(taskId))
                .findFirst()
                .orElseThrow(() -> IntegrationApiException.notFound("TASK_NOT_FOUND", "Task"));
        entityManager.refresh(task.getRequest());
        entityManager.refresh(task.getRequest().getReleaseFlow());
        assertAuthorized(task, actor);
        assertProvisionable(task);
        assertNoSecretLikeValues(command);

        task.setAssigneeUserId(command.assigneeUserId().trim());
        task.setCapabilityType(command.capability().capabilityType());
        task.setCapabilityId(command.capability().capabilityId().trim());
        task.setCapabilityVersion(command.capability().capabilityVersion().trim());
        if (command.repository() == null) {
            task.setRepositoryId(null);
            task.setRepositoryUrl(null);
            task.setRepositoryProvider(null);
            task.setRepositoryBranch(null);
            task.setRepositoryCommit(null);
        } else {
            task.setRepositoryId(command.repository().repositoryId().trim());
            task.setRepositoryUrl(command.repository().url().trim());
            task.setRepositoryProvider(command.repository().provider().trim());
            task.setRepositoryBranch(command.repository().branch().trim());
            task.setRepositoryCommit(command.repository().commit());
        }
        if (!authorizationService.isIntegrationReady(task)) {
            throw IntegrationApiException.unprocessable(
                    "INVALID_TASK_BINDING",
                    "The Task binding is incomplete or contains unsafe project/repository values.");
        }
        taskRepository.save(task);
        audit(task, actor);
        return projectionService.toTask(task, actor);
    }

    private void assertAuthorized(Task task, IntegrationActor actor) {
        if (actor == null || actor.user() == null || actor.user().isGuestViewer()) {
            throw IntegrationApiException.forbidden("Task binding requires an authenticated administrator.");
        }
        if (actor.bearerAuthenticated()) {
            throw IntegrationApiException.forbidden(
                    "A human Web session is required for Task binding.");
        }
        Request request = task.getRequest();
        if ((!actor.user().isGlobalDevOpsAdmin()
                && !actor.user().hasPermission(PermissionKey.PLATFORM_ACCESS_MANAGE.value()))
                || !actor.allowsAgent(request.getAgent())
                || !actor.user().hasScopedAccess(request.getApplication(), request.getSnowGroup())) {
            throw IntegrationApiException.forbidden("Task binding is outside the current control-plane scope.");
        }
        if (!stagePipelineRegistry.contains(request.getAgent())) {
            throw IntegrationApiException.unprocessable(
                    "INVALID_TASK_BINDING", "The Request Agent Module is not registered.");
        }
        if (request.getArchivedAt() != null || request.getReleaseFlow().getArchivedAt() != null) {
            throw IntegrationApiException.conflict(
                    "TASK_NOT_EXECUTABLE", "Archived work cannot be provisioned.", false);
        }
    }

    private void assertProvisionable(Task task) {
        if (task.getActiveExecutionId() != null
                || task.getLatestExecutionId() != null
                || executionRepository.countByTaskId(task.getId()) != 0
                || (task.getTaskStatus() != TaskStatus.Pending
                && task.getTaskStatus() != TaskStatus.Ready_For_Execution)) {
            throw IntegrationApiException.conflict(
                    "TASK_BINDING_LOCKED",
                    "A Task can be bound only before its first execution.",
                    false);
        }
    }

    private static void assertNoSecretLikeValues(IntegrationTaskBindingRequest command) {
        List<String> values = new java.util.ArrayList<>();
        values.add(command.assigneeUserId());
        values.add(command.capability().capabilityId());
        values.add(command.capability().capabilityVersion());
        if (command.repository() != null) {
            values.add(command.repository().repositoryId());
            values.add(command.repository().url());
            values.add(command.repository().provider());
            values.add(command.repository().branch());
            values.add(command.repository().commit());
        }
        if (values.stream().anyMatch(value -> value != null
                && !SensitiveTextRedactor.redact(value).equals(value))) {
            throw IntegrationApiException.unprocessable(
                    "INVALID_TASK_BINDING",
                    "The Task binding contains prohibited secret-like metadata.");
        }
    }

    private List<Task> lockRequestTasks(String requestId) {
        List<String> taskIds = taskRepository
                .findByRequestIdOrderByTaskGroupIdAscStepSeqAsc(requestId)
                .stream()
                .map(Task::getId)
                .sorted()
                .toList();
        return taskIds.stream()
                .map(id -> taskRepository.findByIdForExecutionUpdate(id)
                        .orElseThrow(() -> IntegrationApiException.notFound("TASK_NOT_FOUND", "Task")))
                .peek(entityManager::refresh)
                .toList();
    }

    private void audit(Task task, IntegrationActor actor) {
        Request request = task.getRequest();
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("capabilityType", task.getCapabilityType().name());
        context.put("capabilityId", task.getCapabilityId());
        context.put("assigneeUserId", task.getAssigneeUserId());
        context.put("application", request.getApplication());
        context.put("snowGroup", request.getSnowGroup());
        context.put("agent", request.getAgent());
        context.put("correlationId", CorrelationIdFilter.current());
        auditLogger.logAtomic(
                actor.user(),
                AuditActionType.integration_task_binding_update,
                request.getReleaseFlow().getId(),
                request.getId(),
                task.getId(),
                context);
    }
}
