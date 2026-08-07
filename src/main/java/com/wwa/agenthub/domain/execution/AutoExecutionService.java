package com.wwa.agenthub.domain.execution;

import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.enums.AuditActionType;
import com.wwa.agenthub.contracts.enums.ExecutionStatus;
import com.wwa.agenthub.contracts.enums.ExecutionType;
import com.wwa.agenthub.contracts.enums.ExternalStatus;
import com.wwa.agenthub.contracts.enums.TaskStatus;
import com.wwa.agenthub.domain.audit.AuditLoggerService;
import com.wwa.agenthub.domain.configuration.ConfigurationComponentService;
import com.wwa.agenthub.domain.configuration.ConfigurationScope;
import com.wwa.agenthub.domain.decision.ReleaseFlowProgressionService;
import com.wwa.agenthub.domain.task.*;
import com.wwa.agenthub.errors.ConflictAppException;
import com.wwa.agenthub.errors.NotFoundAppException;
import com.wwa.agenthub.errors.ValidationAppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AutoExecutionService – resolves the external tool target, submits AUTO tasks,
 * and records rich execution metadata immediately after submission.
 *
 * <p>Guards:
 * <ul>
 *   <li>Task must be {@code AUTO} execution type</li>
 *   <li>Task must be in {@code Ready_For_Execution} state</li>
 * </ul>
 *
 * <p>Flow:
 * <ol>
 *   <li>Resolve external tool target via {@link ExecutionTargetResolver}</li>
 *   <li>Create execution history record and transition task to {@code Executing}</li>
 *   <li>Call the correct adapter (Jenkins/Ansible)</li>
 *   <li>Persist initial external references and seed {@code QUEUED} status</li>
 *   <li>On failure: mark task as {@code Failed}, trigger progression recompute</li>
 *   <li>Audit the action</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoExecutionService {

    private static final int URL_MAX_LENGTH = 2000;
    private static final int STATUS_MESSAGE_MAX_LENGTH = 2000;

    private final TaskRepository taskRepository;
    private final TaskExecutionHistoryRepository executionHistoryRepository;
    private final TaskPermissionService taskPermissionService;
    private final List<AutoExecutionAdapter> adapters;
    private final ExecutionTargetResolver targetResolver;
    private final ConfigurationComponentService configurationComponentService;
    private final AuditLoggerService auditLogger;
    private final ReleaseFlowProgressionService progressionService;
    private final TaskService taskService;
    private final Clock clock;

    @Transactional
    public Task submitAutoExecution(String taskId, UserContext user) {
        Task task = taskRepository.findByIdForExecutionUpdate(taskId)
                .orElseThrow(() -> new NotFoundAppException("Task", taskId));
        taskService.assertTaskRequestActive(task);
        taskPermissionService.assertOwnerOrAdmin(task, user, "task:submitAutoExecution");
        if (task.isIntegrationBound()) {
            throw new ConflictAppException(
                    "Integration-bound Tasks must be started through the Atlas Integration Execution API");
        }

        if (task.getExecutionType() != ExecutionType.AUTO) {
            throw new ConflictAppException(
                    "Task " + taskId + " is not an AUTO task; cannot submit for auto execution");
        }
        if (task.getTaskStatus() != TaskStatus.Ready_For_Execution) {
            throw new ConflictAppException(
                    "Task must be in Ready_For_Execution state, current: " + task.getTaskStatus().name());
        }

        // Resolve target (throws ValidationAppException on bad input)
        Map<String, Object> inputParams = task.getInputParameters() != null ? task.getInputParameters() : Map.of();
        ExecutionTarget target = targetResolver.resolve(inputParams);
        AutoExecutionAdapter adapter = findAdapter(target.systemType());
        ConfigurationScope scope = ConfigurationScope.from(task.getRequest());

        // Pre-flight: verify external system configuration is available
        try {
            configurationComponentService.resolveForSystem(target.systemType(), scope);
        } catch (Exception e) {
            throw new ValidationAppException(
                    target.systemType() + " configuration is not ready. "
                    + "Please configure the endpoint URL and credentials in Configuration Management "
                    + "before running AUTO tasks. (" + e.getMessage() + ")");
        }

        // Create execution history record
        int maxAttempt = executionHistoryRepository.findMaxAttemptNumberByTaskId(taskId);
        int nextAttempt = maxAttempt + 1;

        TaskExecutionHistory history = new TaskExecutionHistory();
        history.setTask(task);
        history.setAttemptNumber(nextAttempt);
        history.setExecutionStatus(ExecutionStatus.Running);
        history.setInputSnapshot(task.getInputParameters());
        history.setStartTime(clock.instant());
        history.setExternalSystemType(target.systemType());
        history.setSubmittedAt(clock.instant());
        history.setExternalStatus(ExternalStatus.QUEUED);
        history.setExternalStatusMessage("Submitting to " + target.systemType());
        history.setConfigApplication(scope.application());
        history.setConfigSnowGroup(scope.snowGroup());
        history.setConfigAgent(scope.agent());
        TaskExecutionHistory savedHistory = executionHistoryRepository.save(history);

        // Transition task to Executing
        task.setTaskStatus(TaskStatus.Executing);
        task.setLatestExecutionId(savedHistory.getId());
        task.setStartTime(clock.instant());

        // Call external system
        AutoSubmissionResult result = adapter.submit(target, inputParams, scope);

        if (result.success()) {
            savedHistory.setSubmissionStatus("SUBMITTED");
            savedHistory.setSubmissionMessage(truncate(result.message(), STATUS_MESSAGE_MAX_LENGTH));
            savedHistory.setExternalExecutionId(result.executionId());
            savedHistory.setExternalJobUrl(truncate(result.jobUrl(), URL_MAX_LENGTH));
            savedHistory.setExternalLogUrl(truncate(result.logUrl(), URL_MAX_LENGTH));
            savedHistory.setExternalApprovalUrl(truncate(result.approvalUrl(), URL_MAX_LENGTH));
            savedHistory.setExternalStatus(ExternalStatus.QUEUED);
            savedHistory.setExternalStatusMessage("Queued in " + target.systemType());
        } else {
            savedHistory.setSubmissionStatus("FAILED");
            savedHistory.setSubmissionMessage(truncate(result.message(), STATUS_MESSAGE_MAX_LENGTH));
            savedHistory.setExecutionStatus(ExecutionStatus.Failed);
            savedHistory.setExternalStatus(ExternalStatus.FAILED);
            savedHistory.setExternalStatusMessage(truncate(result.message(), STATUS_MESSAGE_MAX_LENGTH));
            savedHistory.setEndTime(clock.instant());
            task.setTaskStatus(TaskStatus.Failed);
            task.setEndTime(clock.instant());
        }

        executionHistoryRepository.save(savedHistory);
        Task savedTask = taskRepository.save(task);

        // Audit
        Map<String, Object> auditContext = new HashMap<>();
        auditContext.put("action", "auto_submit");
        auditContext.put("systemType", target.systemType());
        auditContext.put("targetKind", target.targetKind());
        auditContext.put("attemptNumber", nextAttempt);
        auditContext.put("submissionStatus", savedHistory.getSubmissionStatus());
        if (scope.application() != null) {
            auditContext.put("application", scope.application());
        }
        if (scope.snowGroup() != null) {
            auditContext.put("snowGroup", scope.snowGroup());
        }
        if (scope.agent() != null) {
            auditContext.put("agent", scope.agent());
        }
        if (result.jobUrl() != null) {
            auditContext.put("externalJobUrl", result.jobUrl());
        }

        auditLogger.log(user, AuditActionType.auto_submit,
                task.getRequest().getReleaseFlow().getId(),
                task.getRequest().getId(),
                taskId,
                auditContext);

        // If submission failed, trigger progression so flow status is recomputed
        if (!result.success()) {
            progressionService.progressAfterDecision(taskId);
        }

        return savedTask;
    }

    private AutoExecutionAdapter findAdapter(String systemType) {
        return adapters.stream()
                .filter(a -> a.systemType().equalsIgnoreCase(systemType))
                .findFirst()
                .orElseThrow(() -> new ConflictAppException(
                        "No execution adapter found for system type: " + systemType));
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}
