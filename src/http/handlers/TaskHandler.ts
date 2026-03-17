import { FastifyInstance } from "fastify";
import { TaskService } from "../../domain/task/TaskService";
import { TaskRepository } from "../../domain/task/TaskRepository";
import { TaskExecutionHistoryService } from "../../domain/task/TaskExecutionHistoryService";
import { TaskDto, TaskExecutionHistoryDto, PaginatedResponseDto } from "../../contracts/dtos";
import { requireRole } from "../middleware/auth";
import { ValidationError } from "../../errors/AppError";
import { z } from "zod";

/**
 * Task handler – REST endpoints for Task lifecycle and result viewing.
 *
 * GET /api/deployment-agent/tasks?requestId=X – list tasks for a request
 * GET /api/deployment-agent/tasks/:id – task detail with execution history
 * PUT /api/deployment-agent/tasks/:id/input – edit task input (TL auth)
 * GET /api/deployment-agent/tasks/:id/executions – execution history
 */
export function registerTaskRoutes(
  app: FastifyInstance,
  taskService: TaskService,
  _taskRepo: TaskRepository,
  executionHistoryService: TaskExecutionHistoryService
): void {
  // Helper: map task entity to DTO
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  function mapTaskToDto(task: any): TaskDto {
    return {
      id: task.id,
      requestId: task.requestId,
      taskGroupId: task.taskGroupId,
      taskGroupName: task.taskGroupName,
      stepSeq: task.stepSeq,
      taskName: task.taskName,
      executionType: task.executionType,
      taskStatus: task.taskStatus,
      inputParameters: task.inputParameters,
      expectedOutput: task.expectedOutput ?? null,
      owner: task.owner ?? null,
      plannedStartTime: task.plannedStartTime?.toISOString() ?? null,
      plannedEndTime: task.plannedEndTime?.toISOString() ?? null,
      currentResultSummary: task.currentResultSummary,
      latestExecutionId: task.latestExecutionId,
      version: task.version,
    };
  }

  // Helper: map execution history entity to DTO
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  function mapExecutionToDto(execution: any): TaskExecutionHistoryDto {
    return {
      id: execution.id,
      taskId: execution.taskId,
      attemptNumber: execution.attemptNumber,
      executionStatus: execution.executionStatus,
      inputSnapshot: execution.inputSnapshot,
      resultSummary: execution.resultSummary,
      resultLogs: execution.resultLogs ?? null,
      startTime: execution.startTime.toISOString(),
      endTime: execution.endTime?.toISOString() ?? null,
    };
  }

  // GET /api/deployment-agent/tasks?requestId=X
  app.get<{
    Querystring: {
      requestId?: string;
    };
  }>("/api/deployment-agent/tasks", async (req, reply) => {
    const { requestId } = req.query;

    if (!requestId || typeof requestId !== "string") {
      throw new ValidationError("requestId query parameter is required", { requestId });
    }

    const tasks = await taskService.listByRequestId(requestId);
    const dtos = tasks.map(mapTaskToDto);

    const response: PaginatedResponseDto<TaskDto> = {
      data: dtos,
      total: dtos.length,
      page: 0,
      size: dtos.length || 10,
    };

    return reply.send(response);
  });

  // GET /api/deployment-agent/tasks/:id
  app.get<{
    Params: {
      id: string;
    };
  }>("/api/deployment-agent/tasks/:id", async (req, reply) => {
    const task = await taskService.getById(req.params.id);
    const dto = mapTaskToDto(task);
    return reply.send(dto);
  });

  // PUT /api/deployment-agent/tasks/:id/input
  app.put<{
    Params: {
      id: string;
    };
    Body: unknown;
  }>("/api/deployment-agent/tasks/:id/input", async (req, reply) => {
    const taskId = req.params.id;
    requireRole(req, "task_edit", "TL");
    const userContext = req.userContext!;

    // Validate request body – should contain input JSON
    const inputSchema = z.object({
      input: z.any(),
    });

    const parseResult = inputSchema.safeParse(req.body);
    if (!parseResult.success) {
      throw new ValidationError("Invalid task input edit request", {
        errors: parseResult.error.errors,
      });
    }

    const { input } = parseResult.data;

    // Convert input to JSON string and edit
    const inputJson = JSON.stringify(input);
    const updated = await taskService.editInput(taskId, inputJson, userContext);

    return reply.send(mapTaskToDto(updated));
  });

  // GET /api/deployment-agent/tasks/:id/executions
  app.get<{
    Params: {
      id: string;
    };
    Querystring: {
      page?: string;
      size?: string;
    };
  }>("/api/deployment-agent/tasks/:id/executions", async (req, reply) => {
    const taskId = req.params.id;
    const { page = "0", size = "50" } = req.query;

    const pageNum = parseInt(page, 10);
    const sizeNum = parseInt(size, 10);

    if (isNaN(pageNum) || pageNum < 0) {
      throw new ValidationError("Invalid page parameter", { page });
    }

    if (isNaN(sizeNum) || sizeNum < 1) {
      throw new ValidationError("Invalid size parameter", { size });
    }

    const executions = await executionHistoryService.findByTaskId(taskId);

    // Manual pagination (all executions for task are typically small)
    const paginatedExecutions = executions.slice(
      pageNum * sizeNum,
      (pageNum + 1) * sizeNum
    );

    const dtos = paginatedExecutions.map(mapExecutionToDto);

    const response: PaginatedResponseDto<TaskExecutionHistoryDto> = {
      data: dtos,
      total: executions.length,
      page: pageNum,
      size: sizeNum,
    };

    return reply.send(response);
  });
}
