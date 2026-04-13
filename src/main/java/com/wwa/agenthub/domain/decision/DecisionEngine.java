package com.wwa.agenthub.domain.decision;

import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.enums.AuditActionType;
import com.wwa.agenthub.contracts.enums.TaskStatus;
import com.wwa.agenthub.domain.audit.AuditLoggerService;
import com.wwa.agenthub.domain.eventing.DomainEvent;
import com.wwa.agenthub.domain.eventing.DomainEventPublisher;
import com.wwa.agenthub.domain.task.Task;
import com.wwa.agenthub.domain.task.TaskPermissionService;
import com.wwa.agenthub.domain.task.TaskExecutionHistoryService;
import com.wwa.agenthub.domain.task.TaskService;
import com.wwa.agenthub.errors.InvalidStateTransitionException;
import com.wwa.agenthub.platform.web.common.CorrelationIdFilter;
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
    private final DomainEventPublisher domainEventPublisher;

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

        // Idempotency (P1-1): if the task is already in the terminal state that this
        // decision would produce, treat the call as a successful no-op. This prevents
        // double-click/retry scenarios from producing confusing 422 responses. The
        // permission check still runs first so that an unauthorized caller cannot
        // probe the current task state by calling the decision endpoint.
        //
        // Rerun is intentionally excluded from this rule: it is a "try again" action
        // that must create a fresh execution history row every time it is invoked,
        // even when the task is already in Ready_For_Execution.
        if (isAlreadyInTargetState(task.getTaskStatus(), decision)) {
            return;
        }

        GateOutcome gateOutcome = decisionGate.evaluate(task, decision, user);
        if (!gateOutcome.allowed()) {
            throw new InvalidStateTransitionException(
                    task.getTaskStatus().name(),
                    decision.name(),
                    gateOutcome.reason() != null ? gateOutcome.reason() : "Decision blocked by gate");
        }

        String releaseFlowId = task.getRequest().getReleaseFlow().getId();
        String requestId = task.getRequest().getId();
        TaskStatus previousStatus = task.getTaskStatus();

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

        // Publish a domain event onto the transactional outbox (P2-2 seam).
        // MVP: no consumer reads this; rows accumulate in PENDING state. The
        // future email notification dispatcher will consume task.* events.
        // Event publication is inside the same @Transactional boundary so the
        // event is durable iff the business state change is durable.
        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("previousStatus", previousStatus.name());
        eventPayload.put("newStatus", targetStatusFor(decision).name());
        eventPayload.put("actorKind", gateOutcome.actorKind().name());
        if (gateOutcome.actorRef() != null) {
            eventPayload.put("actorRef", gateOutcome.actorRef());
        }
        eventPayload.put("releaseFlowId", releaseFlowId);
        eventPayload.put("requestId", requestId);
        eventPayload.put("comment", comment != null ? comment : "");
        domainEventPublisher.publish(new DomainEvent(
                "task." + decision.name(),
                "Task",
                taskId,
                null,
                CorrelationIdFilter.current(),
                eventPayload));
    }

    /**
     * Nominal target status for a non-idempotent decision application.
     * Used for domain event payloads so consumers can read the intended
     * target without replicating the state machine rules. Rerun ends in
     * {@code Ready_For_Execution} even though it also creates a new
     * execution history row.
     */
    private static TaskStatus targetStatusFor(DecisionType decision) {
        return switch (decision) {
            case approve -> TaskStatus.Approved;
            case reject -> TaskStatus.Rejected;
            case rerun -> TaskStatus.Ready_For_Execution;
            case skip -> TaskStatus.Skipped;
        };
    }

    /**
     * Returns true when the requested decision would transition the task to a
     * state the task is already in — used by the idempotency rule in
     * {@link #applyDecision}.
     */
    private static boolean isAlreadyInTargetState(TaskStatus current, DecisionType decision) {
        return switch (decision) {
            case approve -> current == TaskStatus.Approved;
            case reject -> current == TaskStatus.Rejected;
            case skip -> current == TaskStatus.Skipped;
            // Rerun always creates a new attempt — never idempotent.
            case rerun -> false;
        };
    }
}
