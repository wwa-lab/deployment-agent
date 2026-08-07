package com.wwa.agenthub.domain.execution;

import com.wwa.agenthub.contracts.enums.ExecutionStatus;
import com.wwa.agenthub.contracts.enums.ExternalStatus;
import com.wwa.agenthub.contracts.enums.TaskStatus;
import com.wwa.agenthub.domain.decision.ReleaseFlowProgressionService;
import com.wwa.agenthub.domain.task.Task;
import com.wwa.agenthub.domain.task.TaskExecutionHistory;
import com.wwa.agenthub.domain.task.TaskExecutionHistoryRepository;
import com.wwa.agenthub.domain.task.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * ExternalExecutionMonitorService – polls active AUTO executions and reconciles
 * remote terminal states back into Deployment Agent task lifecycle.
 *
 * <h3>Polling model</h3>
 * <ul>
 *   <li>Uses {@code @Scheduled(fixedDelay)} to prevent self-overlap within one instance.</li>
 *   <li>Default delay: 30 s; configurable via {@code execution.monitor.poll-delay-ms}.</li>
 *   <li>Bounded batch (default 50); configurable via {@code execution.monitor.batch-size}.</li>
 *   <li>Rows ordered by {@code lastSyncedAt} ASC NULLS FIRST (stalest first).</li>
 * </ul>
 *
 * <h3>Terminal reconciliation (EXE-008)</h3>
 * <ul>
 *   <li>{@code SUCCEEDED} → execution {@code Completed}, task → {@code Awaiting_Review}</li>
 *   <li>{@code FAILED} / {@code ABORTED} → execution {@code Failed}, task → {@code Failed}</li>
 *   <li>{@code TIMED_OUT} → execution {@code Timed_Out}, task → {@code Failed}</li>
 * </ul>
 *
 * <h3>Safety rules</h3>
 * <ul>
 *   <li>Each item is processed in its own transaction so one failure doesn't abort the batch.</li>
 *   <li>Polling errors keep the row eligible for the next cycle (no immediate task failure).</li>
 *   <li>Terminal sync triggers {@link ReleaseFlowProgressionService#progressAfterDecision}
 *       to recompute flow aggregates without bypassing the human review gate.</li>
 * </ul>
 *
 * <h3>Enablement</h3>
 * <p>Set {@code execution.monitor.enabled=true} to activate polling.
 * Default is {@code false} so the scheduler can be enabled progressively in each environment.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalExecutionMonitorService {

    private static final int URL_MAX_LENGTH = 2000;
    private static final int STATUS_MESSAGE_MAX_LENGTH = 2000;

    @Value("${execution.monitor.enabled:false}")
    private boolean enabled;

    @Value("${execution.monitor.batch-size:50}")
    private int batchSize;

    private final TaskExecutionHistoryRepository executionHistoryRepository;
    private final TaskRepository taskRepository;
    private final List<AutoExecutionAdapter> adapters;
    private final ReleaseFlowProgressionService progressionService;
    private final Clock clock;

    /**
     * Main polling loop. Runs with fixedDelay so the next cycle only starts
     * after the current one fully completes.
     */
    @Scheduled(fixedDelayString = "${execution.monitor.poll-delay-ms:30000}")
    public void pollActiveExecutions() {
        if (!enabled) {
            return;
        }

        List<TaskExecutionHistory> batch = executionHistoryRepository.findActiveAutoExecutions(
                ExecutionStatus.Running, PageRequest.of(0, batchSize));

        if (batch.isEmpty()) {
            return;
        }

        log.debug("Monitor poll: {} active executions in batch", batch.size());

        for (TaskExecutionHistory history : batch) {
            try {
                processSingleExecution(history.getId());
            } catch (Exception e) {
                log.error("Monitor: unhandled error processing execution {}: {}",
                        history.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Process one execution attempt in its own transaction.
     * Isolating per-item transactions ensures batch items are independent.
     */
    @Transactional
    public void processSingleExecution(String executionId) {
        TaskExecutionHistory history = executionHistoryRepository.findById(executionId)
                .orElse(null);
        if (history == null) {
            log.warn("Monitor: execution {} not found; skipping", executionId);
            return;
        }

        // Guard: skip if no longer Running (may have been updated by another path)
        if (history.getExecutionStatus() != ExecutionStatus.Running) {
            return;
        }
        if (history.isIntegrationManaged()) {
            log.warn("Monitor: Integration-managed execution {} cannot be reconciled by an external adapter",
                    executionId);
            return;
        }

        String systemType = history.getExternalSystemType();
        AutoExecutionAdapter adapter = adapters.stream()
                .filter(a -> a.systemType().equalsIgnoreCase(systemType))
                .findFirst()
                .orElse(null);

        if (adapter == null) {
            log.warn("Monitor: no adapter for system type '{}' on execution {}", systemType, executionId);
            return;
        }

        AutoPollResult pollResult = adapter.pollStatus(history);
        applyPollResult(history, pollResult);
    }

    // ─── Apply poll result ─────────────────────────────────────────────────────

    private void applyPollResult(TaskExecutionHistory history, AutoPollResult poll) {
        Instant now = clock.instant();

        // Always update normalized external status fields
        if (poll.externalStatus() != null && poll.externalStatus() != ExternalStatus.UNKNOWN) {
            history.setExternalStatus(poll.externalStatus());
        }
        if (poll.statusMessage() != null) {
            history.setExternalStatusMessage(truncate(poll.statusMessage(), STATUS_MESSAGE_MAX_LENGTH));
        }
        if (poll.jobUrl() != null) {
            history.setExternalJobUrl(truncate(poll.jobUrl(), URL_MAX_LENGTH));
        }
        if (poll.logUrl() != null) {
            history.setExternalLogUrl(truncate(poll.logUrl(), URL_MAX_LENGTH));
        }
        if (poll.approvalUrl() != null) {
            history.setExternalApprovalUrl(truncate(poll.approvalUrl(), URL_MAX_LENGTH));
        }
        if (poll.externalExecutionId() != null) {
            history.setExternalExecutionId(poll.externalExecutionId());
        }
        history.setLastSyncedAt(now);

        if (!poll.terminal()) {
            executionHistoryRepository.save(history);
            return;
        }

        // Terminal state handling
        history.setExecutionStatus(poll.executionStatus());
        history.setEndTime(now);
        if (poll.resultSummary() != null) {
            history.setResultSummary(poll.resultSummary());
        }
        if (poll.resultLogs() != null) {
            history.setResultLogs(poll.resultLogs());
        }
        executionHistoryRepository.save(history);

        // Reconcile into task lifecycle
        Task task = history.getTask();
        task = taskRepository.findById(task.getId()).orElse(null);
        if (task == null) {
            log.error("Monitor: task not found for execution {}; cannot reconcile state", history.getId());
            return;
        }

        if (task.isIntegrationBound() || task.getActiveExecutionId() != null) {
            log.warn("Monitor: refusing legacy reconciliation for Integration-bound task {}", task.getId());
            return;
        }

        // Only reconcile if task is still in Executing (guard against concurrent updates)
        if (task.getTaskStatus() != TaskStatus.Executing) {
            log.debug("Monitor: task {} is already in {}; skipping reconcile",
                    task.getId(), task.getTaskStatus());
            return;
        }

        ExternalStatus externalStatus = poll.externalStatus();

        if (externalStatus == ExternalStatus.SUCCEEDED) {
            task.setTaskStatus(TaskStatus.Awaiting_Review);
            task.setCurrentResultSummary(poll.resultSummary());
            taskRepository.save(task);
            log.info("Monitor: execution {} SUCCEEDED → task {} now Awaiting_Review",
                    history.getId(), task.getId());
        } else {
            // FAILED, ABORTED, TIMED_OUT
            task.setTaskStatus(TaskStatus.Failed);
            task.setEndTime(now);
            taskRepository.save(task);
            log.info("Monitor: execution {} {} → task {} now Failed",
                    history.getId(), externalStatus, task.getId());
        }

        // Recompute flow aggregates (progression service guards against auto-approval)
        try {
            progressionService.progressAfterDecision(task.getId());
        } catch (Exception e) {
            log.error("Monitor: progression recompute failed for task {} after execution {}: {}",
                    task.getId(), history.getId(), e.getMessage(), e);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}
