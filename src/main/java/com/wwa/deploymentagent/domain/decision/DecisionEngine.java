package com.wwa.deploymentagent.domain.decision;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.enums.AuditActionType;
import com.wwa.deploymentagent.contracts.enums.TaskStatus;
import com.wwa.deploymentagent.domain.audit.AuditLoggerService;
import com.wwa.deploymentagent.domain.task.Task;
import com.wwa.deploymentagent.domain.task.TaskPermissionService;
import com.wwa.deploymentagent.domain.task.TaskExecutionHistoryService;
import com.wwa.deploymentagent.domain.task.TaskService;
import com.wwa.deploymentagent.errors.InvalidStateTransitionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * DecisionEngine – Centralized decision application logic.
 *
 * <p>All decisions run within a single transaction and require task-owner or DEVOPS_ADMIN permission.
 *
 * <p>Supported decisions:
 * <ul>
 *   <li>Approve: Awaiting_Review → Approved (owner/admin)</li>
 *   <li>Reject:  Awaiting_Review → Rejected (owner/admin)</li>
 *   <li>Rerun:   Rejected/Failed → Ready_For_Execution (owner/admin), creates new execution history</li>
 *   <li>Skip:    Pending/Ready_For_Execution/Awaiting_Review → Skipped (owner/admin)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class DecisionEngine {

    private final TaskService taskService;
    private final TaskExecutionHistoryService executionHistoryService;
    private final AuditLoggerService auditLogger;
    private final TaskPermissionService taskPermissionService;

    /**
     * Apply a decision to a task.
     * Validates role and state constraints, then persists the result.
     * Audits the decision with an optional comment.
     */
    @Transactional
    public void applyDecision(String taskId, DecisionType decision, UserContext user, String comment) {
        Task task = taskService.getById(taskId);
        taskPermissionService.assertOwnerOrAdmin(task, user, "decision:" + decision.name());
        String releaseFlowId = task.getRequest().getReleaseFlow().getId();
        String requestId = task.getRequest().getId();

        switch (decision) {
            case approve -> {
                if (task.getTaskStatus() != TaskStatus.Awaiting_Review) {
                    throw new InvalidStateTransitionException(
                            task.getTaskStatus().name(), TaskStatus.Approved.name(), "Task");
                }
                taskService.updateStatus(taskId, TaskStatus.Approved, user, comment);
            }
            case reject -> {
                if (task.getTaskStatus() != TaskStatus.Awaiting_Review) {
                    throw new InvalidStateTransitionException(
                            task.getTaskStatus().name(), TaskStatus.Rejected.name(), "Task");
                }
                taskService.updateStatus(taskId, TaskStatus.Rejected, user, comment);
            }
            case rerun -> {
                if (task.getTaskStatus() != TaskStatus.Rejected
                        && task.getTaskStatus() != TaskStatus.Failed) {
                    throw new InvalidStateTransitionException(
                            task.getTaskStatus().name(), TaskStatus.Ready_For_Execution.name(), "Task");
                }
                taskService.updateStatus(taskId, TaskStatus.Ready_For_Execution, user, comment);
                executionHistoryService.createExecution(taskId);
            }
            case skip -> {
                if (task.getTaskStatus() != TaskStatus.Pending
                        && task.getTaskStatus() != TaskStatus.Ready_For_Execution
                        && task.getTaskStatus() != TaskStatus.Awaiting_Review) {
                    throw new InvalidStateTransitionException(
                            task.getTaskStatus().name(), TaskStatus.Skipped.name(), "Task");
                }
                taskService.updateStatus(taskId, TaskStatus.Skipped, user, comment);
            }
        }

        // Log the decision as an audit action
        AuditActionType auditAction = switch (decision) {
            case approve -> AuditActionType.approve;
            case reject  -> AuditActionType.reject;
            case rerun   -> AuditActionType.rerun;
            case skip    -> AuditActionType.skip;
        };

        auditLogger.log(user, auditAction, releaseFlowId, requestId, taskId,
                Map.of("decisionType", decision.name(),
                       "previousStatus", task.getTaskStatus().name(),
                       "comment", comment != null ? comment : ""));
    }
}
