import { DataSource, EntityManager } from "typeorm";
import { UserContext } from "../../contracts/UserContext";
import { ForbiddenError, InvalidStateTransitionError } from "../../errors/AppError";
import { AuditLoggerService } from "../audit/AuditLoggerService";
import { TaskService } from "../task/TaskService";
import { TaskExecutionHistoryService } from "../task/TaskExecutionHistoryService";

export type DecisionType = "approve" | "reject" | "rerun" | "skip";

export interface ApplyDecisionInput {
  taskId: string;
  decision: DecisionType;
  user: UserContext;
  comment?: string;
}

/**
 * DecisionEngine – Centralized decision application logic.
 * All decisions run within a transaction and require TL role.
 *
 * Supported decisions:
 * - Approve: Awaiting_Review → Approved (TL only)
 * - Reject: Awaiting_Review → Rejected (TL only)
 * - Rerun: Rejected/Failed → Ready_For_Execution (TL only), creates new execution history
 * - Skip: Pending/Ready_For_Execution → Skipped (TL only)
 */
export class DecisionEngine {
  constructor(
    private readonly taskService: TaskService,
    private readonly executionHistoryService: TaskExecutionHistoryService,
    private readonly auditLogger: AuditLoggerService,
    private readonly dataSource: DataSource
  ) {}

  /**
   * Apply a decision to a task.
   * Validates role and state transitions, then persists the result.
   * Audits the decision with optional comment.
   */
  async applyDecision(input: ApplyDecisionInput): Promise<void> {
    return this.dataSource.transaction(async (em: EntityManager) => {
      // Check role
      if (input.user.role !== "TL") {
        throw new ForbiddenError(`decision:${input.decision}`);
      }

      // Load task
      const task = await this.taskService.getById(input.taskId, em);

      // Apply decision-specific logic
      switch (input.decision) {
        case "approve": {
          // Awaiting_Review → Approved
          if (task.taskStatus !== "Awaiting_Review") {
            throw new InvalidStateTransitionError(task.taskStatus, "Approved", "Task");
          }
          await this.taskService.updateStatus(input.taskId, "Approved", input.user, input.comment, em);
          break;
        }

        case "reject": {
          // Awaiting_Review → Rejected
          if (task.taskStatus !== "Awaiting_Review") {
            throw new InvalidStateTransitionError(task.taskStatus, "Rejected", "Task");
          }
          await this.taskService.updateStatus(input.taskId, "Rejected", input.user, input.comment, em);
          break;
        }

        case "rerun": {
          // Rejected/Failed → Ready_For_Execution + create new execution
          if (task.taskStatus !== "Rejected" && task.taskStatus !== "Failed") {
            throw new InvalidStateTransitionError(task.taskStatus, "Ready_For_Execution", "Task");
          }
          await this.taskService.updateStatus(
            input.taskId,
            "Ready_For_Execution",
            input.user,
            input.comment,
            em
          );
          // Create new execution history record
          await this.executionHistoryService.createExecution(input.taskId, em);
          break;
        }

        case "skip": {
          // Pending/Ready_For_Execution → Skipped
          if (task.taskStatus !== "Pending" && task.taskStatus !== "Ready_For_Execution") {
            throw new InvalidStateTransitionError(task.taskStatus, "Skipped", "Task");
          }
          await this.taskService.updateStatus(input.taskId, "Skipped", input.user, input.comment, em);
          break;
        }

        default: {
          const _exhaustive: never = input.decision;
          throw new Error(`Unknown decision type: ${_exhaustive}`);
        }
      }

      // Log the decision as an audit action
      await this.auditLogger.log(
        {
          user: input.user,
          actionType: input.decision,
          taskId: input.taskId,
          requestId: task.requestId,
          context: {
            decisionType: input.decision,
            previousStatus: task.taskStatus,
            comment: input.comment,
          },
        },
        em
      );
    });
  }
}
