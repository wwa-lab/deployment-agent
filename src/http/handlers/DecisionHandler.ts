import { FastifyInstance } from "fastify";
import { DecisionEngine } from "../../domain/decision/DecisionEngine";
import { ReleaseFlowProgressionService } from "../../domain/decision/ReleaseFlowProgressionService";
import { TaskService } from "../../domain/task/TaskService";
import { DecisionRequestDtoSchema, TaskDto } from "../../contracts/dtos";
import { requireRole } from "../middleware/auth";
import { ValidationError } from "../../errors/AppError";

/**
 * Decision handler – REST endpoint for applying task decisions.
 *
 * POST /api/deployment-agent/tasks/:id/decision
 * - Request: { decision, comment? }
 * - Auth: TL role required
 * - Response: Updated TaskDto
 */
export function registerDecisionRoutes(
  app: FastifyInstance,
  decisionEngine: DecisionEngine,
  progressionService: ReleaseFlowProgressionService,
  taskService: TaskService
): void {
  app.post<{ Params: { id: string }; Body: unknown }>(
    "/api/deployment-agent/tasks/:id/decision",
    async (req, reply) => {
      const taskId = req.params.id;
      requireRole(req, "decision", "TL");
      const userContext = req.userContext!;

      // Validate request body
      const parseResult = DecisionRequestDtoSchema.safeParse(req.body);
      if (!parseResult.success) {
        throw new ValidationError("Invalid decision request", parseResult.error.errors);
      }

      const { decision, comment } = parseResult.data;

      // Apply decision
      await decisionEngine.applyDecision({
        taskId,
        decision,
        user: userContext,
        comment,
      });

      // Progress the flow
      await progressionService.progressAfterDecision(taskId);

      // Return updated task
      const updatedTask = await taskService.getById(taskId);
      const taskDto: TaskDto = {
        id: updatedTask.id,
        requestId: updatedTask.requestId,
        taskGroupId: updatedTask.taskGroupId,
        taskGroupName: updatedTask.taskGroupName,
        stepSeq: updatedTask.stepSeq,
        taskName: updatedTask.taskName,
        executionType: updatedTask.executionType,
        taskStatus: updatedTask.taskStatus,
        inputParameters: updatedTask.inputParameters,
        expectedOutput: updatedTask.expectedOutput ?? null,
        owner: updatedTask.owner ?? null,
        plannedStartTime: updatedTask.plannedStartTime?.toISOString() ?? null,
        plannedEndTime: updatedTask.plannedEndTime?.toISOString() ?? null,
        currentResultSummary: updatedTask.currentResultSummary,
        latestExecutionId: updatedTask.latestExecutionId,
        version: updatedTask.version,
      };

      return reply.send(taskDto);
    }
  );
}
