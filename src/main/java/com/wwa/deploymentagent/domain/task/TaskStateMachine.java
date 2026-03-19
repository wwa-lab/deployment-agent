package com.wwa.deploymentagent.domain.task;

import com.wwa.deploymentagent.contracts.enums.TaskStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Pure static utility that defines and validates Task state transitions.
 *
 * <p>Valid transitions:
 * <pre>
 *   Pending             → Ready_For_Execution, Skipped
 *   Ready_For_Execution → Executing, Skipped
 *   Executing           → Awaiting_Review, Failed
 *   Awaiting_Review     → Approved, Rejected
 *   Rejected            → Ready_For_Execution  (rerun)
 *   Failed              → Ready_For_Execution  (rerun)
 *   Approved            → (terminal – no outgoing transitions)
 *   Skipped             → (terminal – no outgoing transitions)
 * </pre>
 *
 * <p>This class has no dependencies and carries no state. All logic is a pure function.
 */
public final class TaskStateMachine {

    private static final Map<TaskStatus, Set<TaskStatus>> TRANSITIONS =
            new EnumMap<>(TaskStatus.class);

    static {
        TRANSITIONS.put(TaskStatus.Pending,
                EnumSet.of(TaskStatus.Ready_For_Execution, TaskStatus.Skipped));
        TRANSITIONS.put(TaskStatus.Ready_For_Execution,
                EnumSet.of(TaskStatus.Executing, TaskStatus.Skipped));
        TRANSITIONS.put(TaskStatus.Executing,
                EnumSet.of(TaskStatus.Awaiting_Review, TaskStatus.Failed));
        TRANSITIONS.put(TaskStatus.Awaiting_Review,
                EnumSet.of(TaskStatus.Approved, TaskStatus.Rejected));
        TRANSITIONS.put(TaskStatus.Approved, EnumSet.noneOf(TaskStatus.class));
        TRANSITIONS.put(TaskStatus.Rejected,
                EnumSet.of(TaskStatus.Ready_For_Execution));
        TRANSITIONS.put(TaskStatus.Skipped, EnumSet.noneOf(TaskStatus.class));
        TRANSITIONS.put(TaskStatus.Failed,
                EnumSet.of(TaskStatus.Ready_For_Execution));
    }

    private TaskStateMachine() {}

    /**
     * Returns {@code true} if transitioning from {@code from} to {@code to} is permitted.
     */
    public static boolean isValid(TaskStatus from, TaskStatus to) {
        Set<TaskStatus> allowed = TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }
}
