package com.wwa.deploymentagent.domain.task;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.enums.AuditActionType;
import com.wwa.deploymentagent.contracts.enums.ExecutionStatus;
import com.wwa.deploymentagent.contracts.enums.ExecutionType;
import com.wwa.deploymentagent.contracts.enums.TaskStatus;
import com.wwa.deploymentagent.domain.audit.AuditLoggerService;
import com.wwa.deploymentagent.domain.decision.ReleaseFlowProgressionService;
import com.wwa.deploymentagent.errors.ConflictAppException;
import com.wwa.deploymentagent.errors.NotFoundAppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/**
 * RecordResultService – records the outcome of a MANUAL task execution.
 *
 * <p>Guards:
 * <ul>
 *   <li>Task must be {@code MANUAL} execution type</li>
 *   <li>Task must be in {@code Ready_For_Execution} state</li>
 * </ul>
 *
 * <p>Flow:
 * <ol>
 *   <li>Creates a {@link TaskExecutionHistory} record with status {@code Completed}</li>
 *   <li>Transitions task to {@code Awaiting_Review}</li>
 *   <li>Audits the action</li>
 *   <li>Triggers {@link ReleaseFlowProgressionService#progressAfterDecision}</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class RecordResultService {

    private final TaskRepository taskRepository;
    private final TaskExecutionHistoryRepository executionHistoryRepository;
    private final ReleaseFlowProgressionService progressionService;
    private final AuditLoggerService auditLogger;

    @Transactional
    public Task recordResult(String taskId,
                             Map<String, Object> resultSummary,
                             String resultLogs,
                             UserContext user) {

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundAppException("Task", taskId));

        if (task.getExecutionType() != ExecutionType.MANUAL) {
            throw new ConflictAppException(
                    "Task " + taskId + " is not a MANUAL task; cannot record result manually");
        }
        if (task.getTaskStatus() != TaskStatus.Ready_For_Execution) {
            throw new ConflictAppException(
                    "Task must be in Ready_For_Execution state, current: " + task.getTaskStatus().name());
        }

        // Create execution history record
        int maxAttempt = executionHistoryRepository.findMaxAttemptNumberByTaskId(taskId);
        int nextAttempt = maxAttempt + 1;

        TaskExecutionHistory history = new TaskExecutionHistory();
        history.setTask(task);
        history.setAttemptNumber(nextAttempt);
        history.setExecutionStatus(ExecutionStatus.Completed);
        history.setInputSnapshot(task.getInputParameters());
        history.setResultSummary(resultSummary);
        history.setResultLogs(resultLogs);
        history.setStartTime(task.getLastUpdatedAt() != null ? task.getLastUpdatedAt() : Instant.now());
        history.setEndTime(Instant.now());
        TaskExecutionHistory savedHistory = executionHistoryRepository.save(history);

        // Transition task to Awaiting_Review
        task.setTaskStatus(TaskStatus.Awaiting_Review);
        task.setLatestExecutionId(savedHistory.getId());
        task.setCurrentResultSummary(resultSummary);
        Task savedTask = taskRepository.save(task);

        // Audit
        auditLogger.log(user, AuditActionType.view_result,
                task.getRequest().getReleaseFlow().getId(),
                task.getRequest().getId(),
                taskId,
                Map.of("action", "record_result", "attemptNumber", nextAttempt));

        // Trigger flow progression
        progressionService.progressAfterDecision(taskId);

        return savedTask;
    }
}
