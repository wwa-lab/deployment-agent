package com.wwa.agenthub.domain.task;

import com.wwa.agenthub.contracts.enums.ExecutionStatus;
import com.wwa.agenthub.errors.NotFoundAppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * TaskExecutionHistoryService – Execution attempt tracking and lifecycle.
 * Each task rerun creates a new execution history record with an incremented attemptNumber.
 * The current task input is snapshotted at execution time.
 */
@Service
@RequiredArgsConstructor
public class TaskExecutionHistoryService {

    private final TaskExecutionHistoryRepository executionHistoryRepository;
    private final TaskRepository taskRepository;
    private final TaskService taskService;
    private final Clock clock;

    /**
     * Create a new execution history record for a task.
     * <ul>
     *   <li>Gets the next attemptNumber (max + 1)</li>
     *   <li>Snapshots the current task inputParameters</li>
     *   <li>Sets executionStatus = Running</li>
     *   <li>Updates Task.latestExecutionId</li>
     * </ul>
     */
    @Transactional
    public TaskExecutionHistory createExecution(String taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundAppException("Task", taskId));
        taskService.assertTaskRequestActive(task);

        int maxAttempt = executionHistoryRepository.findMaxAttemptNumberByTaskId(taskId);
        int nextAttempt = maxAttempt + 1;

        TaskExecutionHistory execution = new TaskExecutionHistory();
        execution.setTask(task);
        execution.setAttemptNumber(nextAttempt);
        execution.setExecutionStatus(ExecutionStatus.Running);
        execution.setInputSnapshot(task.getInputParameters());
        execution.setResultSummary(null);
        execution.setResultLogs(null);
        execution.setStartTime(clock.instant());
        execution.setEndTime(null);

        TaskExecutionHistory saved = executionHistoryRepository.save(execution);

        task.setLatestExecutionId(saved.getId());
        taskRepository.save(task);

        return saved;
    }

    /** Retrieve all execution attempts for a task, ordered by attemptNumber ascending. */
    @Transactional(readOnly = true)
    public List<TaskExecutionHistory> findByTaskId(String taskId) {
        return executionHistoryRepository.findByTaskIdOrderByAttemptNumberAsc(taskId);
    }

    /** Retrieve the latest execution attempt for a task. */
    @Transactional(readOnly = true)
    public Optional<TaskExecutionHistory> findLatest(String taskId) {
        return executionHistoryRepository.findFirstByTaskIdOrderByAttemptNumberDesc(taskId);
    }

    /**
     * Mark an execution attempt as complete with result data.
     * Sets executionStatus, resultSummary, resultLogs, and endTime.
     */
    @Transactional
    public TaskExecutionHistory completeExecution(String executionId,
                                                  ExecutionStatus executionStatus,
                                                  Map<String, Object> resultSummary,
                                                  String resultLogs) {
        TaskExecutionHistory execution = executionHistoryRepository.findById(executionId)
                .orElseThrow(() -> new NotFoundAppException("TaskExecutionHistory", executionId));

        execution.setExecutionStatus(executionStatus);
        if (resultSummary != null) execution.setResultSummary(resultSummary);
        if (resultLogs != null) execution.setResultLogs(resultLogs);
        execution.setEndTime(clock.instant());

        return executionHistoryRepository.save(execution);
    }
}
