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

import java.util.LinkedHashMap;
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
    private final DecisionGate decisionGate;

    /**
     * Apply a decision to a task.
     * Validates role and state constraints, then persists the result.
     * Audits the decision with an optional comment.
     *
     * <p>MVP Foundation Seam: the decision is first evaluated by
     * {@link DecisionGate#evaluate}. In MVP the only implementation is
     * {@link ManualDecisionGate}, which always returns
     * {@link GateOutcome#proceedAsHuman(UserContext)}. The gate's actor
     * attribution is threaded into the audit context so future policy /
     * AI-assisted outcomes will be reflected in audit rows without touching
     * call sites. See {@code docs/04-architecture/architecture.md}
     * §MVP Foundation Seams.
     */
    @Transactional
    public void applyDecision(String taskId, DecisionType decision, UserContext user, String comment) {
        Task task = taskService.getById(taskId);
        taskPermissionService.assertOwnerOrAdmin(task, user, "decision:" + decision.name());

        GateOutcome gateOutcome = decisionGate.evaluate(task, decision, user);
        if (!gateOutcome.allowed()) {
            throw new InvalidStateTransitionException(
                    task.getTaskStatus().name(),
                    decision.name(),
                    gateOutcome.reason() != null ? gateOutcome.reason() : "Decision blocked by gate");
        }

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

        Map<String, Object> auditContext = new LinkedHashMap<>();
        auditContext.put("decisionType", decision.name());
        auditContext.put("previousStatus", task.getTaskStatus().name());
        auditContext.put("comment", comment != null ? comment : "");
        auditContext.put("actorKind", gateOutcome.actorKind().name());
        if (gateOutcome.actorRef() != null) {
            auditContext.put("actorRef", gateOutcome.actorRef());
        }
        auditLogger.log(user, auditAction, releaseFlowId, requestId, taskId, auditContext);
    }
}
