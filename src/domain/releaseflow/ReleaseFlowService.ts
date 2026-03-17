import { STAGES, Stage } from "../../contracts/enums";
import { NotFoundError } from "../../errors/AppError";
import { ReleaseFlowEntity } from "./ReleaseFlow.entity";
import {
  ReleaseFlowFilter,
  ReleaseFlowRepository,
  PageOptions,
  PageResult,
} from "./ReleaseFlowRepository";
import { RequestRepository } from "./RequestRepository";
import {
  aggregateRequestsToStageStatus,
  aggregateStagesToFlowStatus,
  aggregateTasksToRequestStatus,
} from "./releaseFlowAggregation";

export interface CreateReleaseFlowInput {
  /** From Excel "Project ID". Primary grouping key for lookup. */
  projectId: string;
  /** From Excel "Project Name". Display label. */
  projectName: string;
  /** System-generated release ID. Format: {stage}-{normalized_project_name}-{seq}. */
  releaseId: string | null;
  normalizedReleaseId: string;
  firstStage: Stage;
}

/**
 * ReleaseFlowService – Release Flow lifecycle management and aggregation.
 * Provides hierarchical state aggregation (task → request → stage → flow).
 *
 * Transaction boundaries: callers wrapping multi-entity operations should pass
 * an EntityManager from DataSource.transaction(). Single-entity reads and writes
 * use the repository's default manager.
 */
export class ReleaseFlowService {
  constructor(
    _ds: unknown, // reserved for future transaction support
    private readonly releaseFlowRepo: ReleaseFlowRepository,
    private readonly requestRepo: RequestRepository
  ) {}

  /** Retrieve a Release Flow with its immediate children. Throws NotFoundError if absent. */
  async getById(id: string): Promise<ReleaseFlowEntity> {
    const rf = await this.releaseFlowRepo.findById(id);
    if (!rf) throw new NotFoundError("ReleaseFlow", id);
    return rf;
  }

  /** Paginated list with optional filters. */
  async list(
    filter: ReleaseFlowFilter,
    page: PageOptions
  ): Promise<PageResult<ReleaseFlowEntity>> {
    return this.releaseFlowRepo.findAll(filter, page);
  }

  /**
   * Find existing Release Flow by grouping key (projectId, normalizedReleaseId).
   * Returns null if not found.
   */
  async findByGroupKey(
    projectId: string,
    normalizedReleaseId: string
  ): Promise<ReleaseFlowEntity | null> {
    return this.releaseFlowRepo.findByProjectIdAndNormalizedReleaseId(
      projectId,
      normalizedReleaseId
    );
  }

  /**
   * Create a new Release Flow.
   * To be called inside a transaction during file import (T6.x).
   */
  async create(input: CreateReleaseFlowInput): Promise<ReleaseFlowEntity> {
    const entity = new ReleaseFlowEntity();
    entity.projectId = input.projectId;
    entity.projectName = input.projectName;
    entity.releaseId = input.releaseId;
    entity.normalizedReleaseId = input.normalizedReleaseId;
    entity.currentStage = input.firstStage;
    entity.flowStatus = "Pending";
    entity.reviewStatus = "Pending_Review";
    entity.reviewOwner = null;
    entity.requests = [];
    return this.releaseFlowRepo.save(entity);
  }

  /**
   * Recompute and persist the Release Flow status from current child states.
   * Reads all Requests and their Tasks, aggregates bottom-up, then updates the flow.
   * Called after any state-changing operation (callback, decision, progression).
   */
  async recomputeAndPersistStatus(releaseFlowId: string): Promise<void> {
    const rf = await this.releaseFlowRepo.findById(releaseFlowId);
    if (!rf) throw new NotFoundError("ReleaseFlow", releaseFlowId);

    const requests = await this.requestRepo.findByReleaseFlowId(releaseFlowId);

    // Aggregate task → request status for each request
    for (const req of requests) {
      const taskStatuses = (req.tasks ?? []).map((t) => t.taskStatus);
      const newRequestStatus = aggregateTasksToRequestStatus(taskStatuses);
      if (req.requestStatus !== newRequestStatus) {
        await this.requestRepo.updateStatus(req.id, newRequestStatus);
        req.requestStatus = newRequestStatus;
      }
    }

    // Aggregate request → stage → flow status.
    // Only include stages that have at least one Request – stages with no Requests
    // are not yet active and must not be treated as "Pending" when computing flow completion.
    const stageStatuses = STAGES.flatMap((stage) => {
      const stageRequests = requests.filter((r) => r.stage === stage);
      if (stageRequests.length === 0) return [];
      return [aggregateRequestsToStageStatus(stageRequests.map((r) => r.requestStatus))];
    });

    const newFlowStatus = aggregateStagesToFlowStatus(stageStatuses);
    if (rf.flowStatus !== newFlowStatus) {
      await this.releaseFlowRepo.updateStatus(releaseFlowId, {
        flowStatus: newFlowStatus,
      });
    }
  }

  /** Advance the Release Flow's active stage to the next one in SIT→UAT→PROD order. */
  async advanceStage(releaseFlowId: string): Promise<void> {
    const rf = await this.releaseFlowRepo.findById(releaseFlowId);
    if (!rf) throw new NotFoundError("ReleaseFlow", releaseFlowId);

    const currentIndex = STAGES.indexOf(rf.currentStage);
    if (currentIndex < STAGES.length - 1) {
      const nextStage = STAGES[currentIndex + 1];
      await this.releaseFlowRepo.updateStatus(releaseFlowId, {
        currentStage: nextStage,
        flowStatus: "Running",
      });
    }
  }
}
