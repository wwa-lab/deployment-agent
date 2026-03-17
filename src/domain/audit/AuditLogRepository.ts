import { DataSource, EntityManager, FindOptionsWhere } from "typeorm";
import { AuditActionType } from "../../contracts/enums";
import { AuditLogEntryEntity } from "./AuditLogEntry.entity";
import { PageOptions, PageResult } from "../releaseflow/ReleaseFlowRepository";

export interface AuditLogFilter {
  releaseFlowId?: string;
  taskId?: string;
  operatorId?: string;
  actionType?: AuditActionType;
}

export class AuditLogRepository {
  private readonly repo;

  constructor(ds: DataSource) {
    this.repo = ds.getRepository(AuditLogEntryEntity);
  }

  private repoFor(em?: EntityManager) {
    return em ? em.getRepository(AuditLogEntryEntity) : this.repo;
  }

  /**
   * Append a new audit log entry.
   * This is the ONLY write operation on audit log.
   * No update or delete methods are exposed (append-only constraint).
   */
  async append(
    entry: Omit<AuditLogEntryEntity, "id" | "timestamp">,
    em?: EntityManager
  ): Promise<AuditLogEntryEntity> {
    const toSave = this.repoFor(em).create(entry as AuditLogEntryEntity);
    return this.repoFor(em).save(toSave);
  }

  async findAll(
    filter: AuditLogFilter,
    page: PageOptions
  ): Promise<PageResult<AuditLogEntryEntity>> {
    const where: FindOptionsWhere<AuditLogEntryEntity> = {};
    if (filter.releaseFlowId) where.releaseFlowId = filter.releaseFlowId;
    if (filter.taskId) where.taskId = filter.taskId;
    if (filter.operatorId) where.operatorId = filter.operatorId;
    if (filter.actionType) where.actionType = filter.actionType;

    const [data, total] = await this.repo.findAndCount({
      where,
      skip: page.page * page.size,
      take: page.size,
      order: { timestamp: "DESC" },
    });
    return { data, total, page: page.page, size: page.size };
  }
}
