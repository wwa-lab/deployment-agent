package com.wwa.deploymentagent.domain.execution;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.enums.AuditActionType;
import com.wwa.deploymentagent.contracts.enums.ExecutionStatus;
import com.wwa.deploymentagent.contracts.enums.ExecutionType;
import com.wwa.deploymentagent.contracts.enums.ExternalStatus;
import com.wwa.deploymentagent.contracts.enums.TaskStatus;
import com.wwa.deploymentagent.domain.audit.AuditLoggerService;
import com.wwa.deploymentagent.domain.decision.ReleaseFlowProgressionService;
import com.wwa.deploymentagent.domain.task.*;
import com.wwa.deploymentagent.errors.ConflictAppException;
import com.wwa.deploymentagent.errors.NotFoundAppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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

    private final TaskRepository taskRepository;
    private final TaskExecutionHistoryRepository executionHistoryRepository;
    private final TaskPermissionService taskPermissionService;
    private final List<AutoExecutionAdapter> adapters;
    private final ExecutionTargetResolver targetResolver;
    private final AuditLoggerService auditLogger;
    private final ReleaseFlowProgressionService progressionService;
    private final TaskService taskService;

    @Transactional
    public Task submitAutoExecution(String taskId, UserContext user) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundAppException("Task", taskId));
        taskService.assertTaskRequestActive(task);
        taskPermissionService.assertOwnerOrAdmin(task, user, "task:submitAutoExecution");

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

        // Create execution history record
        int maxAttempt = executionHistoryRepository.findMaxAttemptNumberByTaskId(taskId);
        int nextAttempt = maxAttempt + 1;

        TaskExecutionHistory history = new TaskExecutionHistory();
        history.setTask(task);
        history.setAttemptNumber(nextAttempt);
        history.setExecutionStatus(ExecutionStatus.Running);
        history.setInputSnapshot(task.getInputParameters());
        history.setStartTime(Instant.now());
        history.setExternalSystemType(target.systemType());
        history.setSubmittedAt(Instant.now());
        history.setExternalStatus(ExternalStatus.QUEUED);
        history.setExternalStatusMessage("Submitting to " + target.systemType());
        TaskExecutionHistory savedHistory = executionHistoryRepository.save(history);

        // Transition task to Executing
        task.setTaskStatus(TaskStatus.Executing);
        task.setLatestExecutionId(savedHistory.getId());
        task.setStartTime(Instant.now());

        // Call external system
        AutoSubmissionResult result = adapter.submit(target, inputParams);

        if (result.success()) {
            savedHistory.setSubmissionStatus("SUBMITTED");
            savedHistory.setSubmissionMessage(result.message());
            savedHistory.setExternalExecutionId(result.executionId());
            savedHistory.setExternalJobUrl(result.jobUrl());
            savedHistory.setExternalLogUrl(result.logUrl());
            savedHistory.setExternalApprovalUrl(result.approvalUrl());
            savedHistory.setExternalStatus(ExternalStatus.QUEUED);
            savedHistory.setExternalStatusMessage("Queued in " + target.systemType());
        } else {
            savedHistory.setSubmissionStatus("FAILED");
            savedHistory.setSubmissionMessage(result.message());
            savedHistory.setExecutionStatus(ExecutionStatus.Failed);
            savedHistory.setExternalStatus(ExternalStatus.FAILED);
            savedHistory.setExternalStatusMessage(result.message());
            savedHistory.setEndTime(Instant.now());
            task.setTaskStatus(TaskStatus.Failed);
            task.setEndTime(Instant.now());
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
}
