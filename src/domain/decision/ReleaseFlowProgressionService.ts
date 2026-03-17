import { EntityManager } from "typeorm";
import { NotFoundError } from "../../errors/AppError";
import { ReleaseFlowService } from "../releaseflow/ReleaseFlowService";
import { ReleaseFlowRepository } from "../releaseflow/ReleaseFlowRepository";
import { RequestService } from "../releaseflow/RequestService";
import { RequestRepository } from "../releaseflow/RequestRepository";
import { TaskRepository } from "../task/TaskRepository";

/**
 * ReleaseFlowProgressionService – Orchestrates flow, request, and task progression
 * after decisions are applied.
 *
 * Responsibilities:
 * 1. Check if all tasks in a request are terminal (Approved/Skipped)
 *    → if yes, mark request Completed
 * 2. If request completed and stage is PROD, mark flow Completed
 * 3. If request completed and stage < PROD, advance flow to next stage and create new Request for that stage
 * 4. Find next Pending task in the same request → Ready_For_Execution
 * 5. Recompute flow status bottom-up
 */
export class ReleaseFlowProgressionService {
  constructor(
    private readonly taskRepo: TaskRepository,
    private readonly requestRepo: RequestRepository,
    _requestService: RequestService,
    private readonly releaseFlowService: ReleaseFlowService,
    private readonly releaseFlowRepo: ReleaseFlowRepository
  ) {}

  /**
   * Progress a release flow after a task decision.
   * Loads the task → request → flow hierarchy and updates state accordingly.
   */
  async progressAfterDecision(taskId: string, em?: EntityManager): Promise<void> {
    // Load task and request
    const task = await this.taskRepo.findById(taskId, em);
    if (!task) throw new NotFoundError("Task", taskId);

    const request = await this.requestRepo.findById(task.requestId, em);
    if (!request) throw new NotFoundError("Request", task.requestId);

    const releaseFlow = await this.releaseFlowRepo.findById(request.releaseFlowId, em);
    if (!releaseFlow) throw new NotFoundError("ReleaseFlow", request.releaseFlowId);

    // Step 1: Check if all tasks in request are terminal
    const requestTasks = await this.taskRepo.findByRequestId(request.id, em);
    const allTasksTerminal = requestTasks.every(
      (t) => t.taskStatus === "Approved" || t.taskStatus === "Skipped"
    );

    if (allTasksTerminal) {
      // Mark request as Completed
      await this.requestRepo.updateStatus(request.id, "Completed", em);

      // Step 2: If PROD stage and request completed, mark flow Completed
      if (releaseFlow.currentStage === "PROD") {
        await this.releaseFlowRepo.updateStatus(releaseFlow.id, { flowStatus: "Completed" }, em);
      } else {
        // Step 3: Advance to next stage
        await this.releaseFlowService.advanceStage(releaseFlow.id);
      }
    } else {
      // Step 4: Auto-ready next Pending task
      const nextPendingTask = requestTasks.find((t) => t.taskStatus === "Pending");
      if (nextPendingTask) {
        await this.taskRepo.updateTask(nextPendingTask, { taskStatus: "Ready_For_Execution" }, em);
      }
    }

    // Step 5: Recompute flow status bottom-up
    await this.releaseFlowService.recomputeAndPersistStatus(request.releaseFlowId);
  }
}
