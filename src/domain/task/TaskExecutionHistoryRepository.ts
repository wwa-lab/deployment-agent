import { DataSource, EntityManager } from "typeorm";
import { TaskExecutionHistoryEntity } from "./TaskExecutionHistory.entity";

export class TaskExecutionHistoryRepository {
  private readonly repo;

  constructor(ds: DataSource) {
    this.repo = ds.getRepository(TaskExecutionHistoryEntity);
  }

  private repoFor(em?: EntityManager) {
    return em ? em.getRepository(TaskExecutionHistoryEntity) : this.repo;
  }

  async findById(id: string, em?: EntityManager): Promise<TaskExecutionHistoryEntity | null> {
    return this.repoFor(em).findOne({ where: { id } });
  }

  async findByTaskId(
    taskId: string,
    em?: EntityManager
  ): Promise<TaskExecutionHistoryEntity[]> {
    return this.repoFor(em).find({
      where: { taskId },
      order: { attemptNumber: "ASC" },
    });
  }

  /**
   * Returns the highest attempt_number for a given task.
   * Returns 0 if no attempts exist yet.
   */
  async getMaxAttemptNumber(taskId: string, em?: EntityManager): Promise<number> {
    const result = await this.repoFor(em)
      .createQueryBuilder("teh")
      .select("MAX(teh.attemptNumber)", "max")
      .where("teh.taskId = :taskId", { taskId })
      .getRawOne<{ max: number | null }>();
    return result?.max ?? 0;
  }

  /** Latest execution attempt for a task (highest attempt_number). */
  async findLatestByTaskId(
    taskId: string,
    em?: EntityManager
  ): Promise<TaskExecutionHistoryEntity | null> {
    return this.repoFor(em).findOne({
      where: { taskId },
      order: { attemptNumber: "DESC" },
    });
  }

  async save(
    entity: TaskExecutionHistoryEntity,
    em?: EntityManager
  ): Promise<TaskExecutionHistoryEntity> {
    return this.repoFor(em).save(entity);
  }
}
