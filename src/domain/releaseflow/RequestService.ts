import { Stage } from "../../contracts/enums";
import { NotFoundError } from "../../errors/AppError";
import { RequestEntity } from "./Request.entity";
import { RequestRepository } from "./RequestRepository";

export interface CreateRequestInput {
  releaseFlowId: string;
  stage: Stage;
}

/**
 * RequestService – stage-scoped request lifecycle management.
 * Requests are created during import (T6.x) and their status
 * is updated by the progression engine (T7.x).
 */
export class RequestService {
  constructor(
    _ds: unknown, // reserved for future transaction support
    private readonly requestRepo: RequestRepository
  ) {}

  /** Retrieve a Request by ID. Throws NotFoundError if absent. */
  async getById(id: string): Promise<RequestEntity> {
    const req = await this.requestRepo.findById(id);
    if (!req) throw new NotFoundError("Request", id);
    return req;
  }

  /** All Requests for a Release Flow, ordered by creation time. */
  async listByReleaseFlow(releaseFlowId: string): Promise<RequestEntity[]> {
    return this.requestRepo.findByReleaseFlowId(releaseFlowId);
  }

  /** Request for a specific stage of a Release Flow (one per stage in MVP). */
  async findByStage(
    releaseFlowId: string,
    stage: Stage
  ): Promise<RequestEntity | null> {
    return this.requestRepo.findByReleaseFlowAndStage(releaseFlowId, stage);
  }

  /**
   * Create a new Request.
   * Normally called within the import transaction (T6.x).
   * To participate in the caller's transaction, wrap in DataSource.transaction()
   * and pass the transactional EntityManager to the repository.
   */
  async create(input: CreateRequestInput): Promise<RequestEntity> {
    const entity = new RequestEntity();
    entity.releaseFlowId = input.releaseFlowId;
    entity.stage = input.stage;
    entity.requestStatus = "Pending";
    entity.tasks = [];
    return this.requestRepo.save(entity);
  }

  async updateStatus(
    id: string,
    status: RequestEntity["requestStatus"]
  ): Promise<void> {
    const req = await this.requestRepo.findById(id);
    if (!req) throw new NotFoundError("Request", id);
    await this.requestRepo.updateStatus(id, status);
  }
}
