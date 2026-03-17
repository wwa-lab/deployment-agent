import { DataSource, EntityManager } from "typeorm";
import { TaskStatus } from "../../contracts/enums";
import { TaskEntity } from "./Task.entity";

export class TaskRepository {
  private readonly repo;

  constructor(ds: DataSource) {
    this.repo = ds.getRepository(TaskEntity);
  }

  private repoFor(em?: EntityManager) {
    return em ? em.getRepository(TaskEntity) : this.repo;
  }

  async findById(id: string, em?: EntityManager): Promise<TaskEntity | null> {
    return this.repoFor(em).findOne({ where: { id } });
  }

  async findByRequestId(requestId: string, em?: EntityManager): Promise<TaskEntity[]> {
    return this.repoFor(em).find({
      where: { requestId },
      order: { taskGroupId: "ASC", stepSeq: "ASC" },
    });
  }

  async findByRequestIdAndStatus(
    requestId: string,
    taskStatus: TaskStatus,
    em?: EntityManager
  ): Promise<TaskEntity[]> {
    return this.repoFor(em).find({ where: { requestId, taskStatus } });
  }

  async save(entity: TaskEntity, em?: EntityManager): Promise<TaskEntity> {
    return this.repoFor(em).save(entity);
  }

  /**
   * Persist a partial update on a task.
   * TypeORM will check the @VersionColumn automatically when the version
   * mismatches – callers should catch OptimisticLockVersionMismatchError
   * and rethrow as OptimisticLockConflictError from src/errors/AppError.ts.
   */
  async updateTask(
    task: TaskEntity,
    patch: Partial<
      Pick<
        TaskEntity,
        | "taskStatus"
        | "inputParametersJson"
        | "currentResultSummaryJson"
        | "latestExecutionId"
        | "startTime"
        | "endTime"
      >
    >,
    em?: EntityManager
  ): Promise<TaskEntity> {
    Object.assign(task, patch);
    return this.repoFor(em).save(task);
  }
}
