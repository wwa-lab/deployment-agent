import { FastifyInstance } from "fastify";
import { ReleaseFlowService } from "../../domain/releaseflow/ReleaseFlowService";
import { ReleaseFlowRepository, ReleaseFlowFilter } from "../../domain/releaseflow/ReleaseFlowRepository";
import { RequestRepository } from "../../domain/releaseflow/RequestRepository";
import { TaskRepository } from "../../domain/task/TaskRepository";
import {
  ReleaseFlowListItemDto,
  ReleaseFlowDetailDto,
  PaginatedResponseDto,
  RequestDto,
  TaskDto,
} from "../../contracts/dtos";
import { ValidationError } from "../../errors/AppError";

/**
 * Release flow handler – REST endpoints for Release Flow lifecycle.
 *
 * GET /api/deployment-agent/release-flows – paginated list with filters
 * GET /api/deployment-agent/release-flows/:id – detail with nested requests/tasks
 */
export function registerReleaseFlowRoutes(
  app: FastifyInstance,
  releaseFlowService: ReleaseFlowService,
  _releaseFlowRepo: ReleaseFlowRepository,
  requestRepo: RequestRepository,
  taskRepo: TaskRepository
): void {
  // Helper: convert task entity to DTO
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

  // Helper: convert request entity to DTO (with optional nested tasks)
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  function mapRequestToDto(request: any, tasks?: any[]): RequestDto {
    return {
      id: request.id,
      releaseFlowId: request.releaseFlowId,
      stage: request.stage,
      requestStatus: request.requestStatus,
      tasks: tasks?.map(mapTaskToDto),
    };
  }

  // Helper: convert release flow entity to list DTO
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  function mapFlowToListItemDto(flow: any): ReleaseFlowListItemDto {
    return {
      id: flow.id,
      projectId: flow.projectId,
      projectName: flow.projectName,
      releaseId: flow.releaseId,
      normalizedReleaseId: flow.normalizedReleaseId,
      currentStage: flow.currentStage,
      flowStatus: flow.flowStatus,
      reviewStatus: flow.reviewStatus,
    };
  }

  // Helper: convert release flow entity to detail DTO (with nested requests/tasks)
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  async function mapFlowToDetailDto(flow: any): Promise<ReleaseFlowDetailDto> {
    const requests = await requestRepo.findByReleaseFlowId(flow.id);

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const requestDtos = await Promise.all(
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      requests.map(async (req: any) => {
        const tasks = await taskRepo.findByRequestId(req.id);
        return mapRequestToDto(req, tasks);
      })
    );

    return {
      id: flow.id,
      projectId: flow.projectId,
      projectName: flow.projectName,
      releaseId: flow.releaseId,
      normalizedReleaseId: flow.normalizedReleaseId,
      currentStage: flow.currentStage,
      flowStatus: flow.flowStatus,
      reviewStatus: flow.reviewStatus,
      requests: requestDtos,
    };
  }

  // GET /api/deployment-agent/release-flows
  app.get<{
    Querystring: {
      project?: string;
      page?: string;
      size?: string;
    };
  }>("/api/deployment-agent/release-flows", async (req, reply) => {
    const { project, page = "0", size = "10" } = req.query;

    const pageNum = parseInt(page, 10);
    const sizeNum = parseInt(size, 10);

    if (isNaN(pageNum) || pageNum < 0) {
      throw new ValidationError("Invalid page parameter", { page });
    }

    if (isNaN(sizeNum) || sizeNum < 1) {
      throw new ValidationError("Invalid size parameter", { size });
    }

    if (sizeNum > 100) {
      throw new ValidationError("Page size cannot exceed 100", { size: sizeNum });
    }

    const filter: ReleaseFlowFilter = {};
    if (project) filter.projectId = project;

    const result = await releaseFlowService.list(filter, { page: pageNum, size: sizeNum });

    const dtos = result.data.map(mapFlowToListItemDto);

    const response: PaginatedResponseDto<ReleaseFlowListItemDto> = {
      data: dtos,
      total: result.total,
      page: result.page,
      size: result.size,
    };

    return reply.send(response);
  });

  // GET /api/deployment-agent/release-flows/:id
  app.get<{
    Params: {
      id: string;
    };
  }>("/api/deployment-agent/release-flows/:id", async (req, reply) => {
    const flow = await releaseFlowService.getById(req.params.id);
    const detailDto = await mapFlowToDetailDto(flow);
    return reply.send(detailDto);
  });
}
