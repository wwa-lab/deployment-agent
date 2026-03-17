import { DataSource, EntityManager, FindOptionsWhere } from "typeorm";
import { FlowStatus, Stage } from "../../contracts/enums";
import { ReleaseFlowEntity } from "./ReleaseFlow.entity";

export interface ReleaseFlowFilter {
  /** Filter by project_id (from Excel "Project ID"). Maps to the ?project= query param. */
  projectId?: string;
  stage?: Stage;
  flowStatus?: FlowStatus;
}

export interface PageOptions {
  page: number;
  size: number;
}

export interface PageResult<T> {
  data: T[];
  total: number;
  page: number;
  size: number;
}

export class ReleaseFlowRepository {
  private readonly repo;

  constructor(ds: DataSource) {
    this.repo = ds.getRepository(ReleaseFlowEntity);
  }

  /** Use within an active transaction by passing a transactional EntityManager. */
  private repoFor(em?: EntityManager) {
    return em ? em.getRepository(ReleaseFlowEntity) : this.repo;
  }

  async findById(id: string, em?: EntityManager): Promise<ReleaseFlowEntity | null> {
    return this.repoFor(em).findOne({ where: { id }, relations: ["requests"] });
  }

  /**
   * Look up an existing Release Flow by grouping key: (projectId, normalizedReleaseId).
   * Used during import to determine whether to create a new RF or attach to an existing one.
   */
  async findByProjectIdAndNormalizedReleaseId(
    projectId: string,
    normalizedReleaseId: string,
    em?: EntityManager
  ): Promise<ReleaseFlowEntity | null> {
    return this.repoFor(em).findOne({
      where: { projectId, normalizedReleaseId },
    });
  }

  async findAll(
    filter: ReleaseFlowFilter,
    page: PageOptions
  ): Promise<PageResult<ReleaseFlowEntity>> {
    const where: FindOptionsWhere<ReleaseFlowEntity> = {};
    if (filter.projectId) where.projectId = filter.projectId;
    if (filter.flowStatus) where.flowStatus = filter.flowStatus;
    if (filter.stage) where.currentStage = filter.stage;

    const [data, total] = await this.repo.findAndCount({
      where,
      skip: page.page * page.size,
      take: page.size,
      order: { updatedAt: "DESC" },
    });
    return { data, total, page: page.page, size: page.size };
  }

  async save(entity: ReleaseFlowEntity, em?: EntityManager): Promise<ReleaseFlowEntity> {
    return this.repoFor(em).save(entity);
  }

  async updateStatus(
    id: string,
    patch: Partial<Pick<ReleaseFlowEntity, "flowStatus" | "reviewStatus" | "currentStage" | "reviewOwner">>,
    em?: EntityManager
  ): Promise<void> {
    await this.repoFor(em).update({ id }, patch);
  }
}
