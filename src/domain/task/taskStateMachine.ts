import { TaskStatus } from "../../contracts/enums";

/**
 * Pure function that determines if a task status transition is valid.
 * Defines the frozen state machine design from T5.1.
 *
 * Valid transitions:
 * - Pending → Ready_For_Execution, Skipped
 * - Ready_For_Execution → Executing, Skipped
 * - Executing → Awaiting_Review, Failed
 * - Awaiting_Review → Approved, Rejected
 * - Rejected → Ready_For_Execution (rerun)
 * - Failed → Ready_For_Execution (rerun)
 *
 * @param fromStatus Current task status
 * @param toStatus Desired task status
 * @returns true if transition is allowed, false otherwise
 */
export function isValidTaskTransition(fromStatus: TaskStatus, toStatus: TaskStatus): boolean {
  const transitions: Record<TaskStatus, TaskStatus[]> = {
    Pending: ["Ready_For_Execution", "Skipped"],
    Ready_For_Execution: ["Executing", "Skipped"],
    Executing: ["Awaiting_Review", "Failed"],
    Awaiting_Review: ["Approved", "Rejected"],
    Approved: [],
    Rejected: ["Ready_For_Execution"],
    Skipped: [],
    Failed: ["Ready_For_Execution"],
  };

  return transitions[fromStatus]?.includes(toStatus) ?? false;
}
