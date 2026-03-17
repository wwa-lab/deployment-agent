import { EntityManager } from "typeorm";
import { ExecutionStatus } from "../../contracts/enums";
import { NotFoundError } from "../../errors/AppError";
import { TaskExecutionHistoryEntity } from "./TaskExecutionHistory.entity";
import { TaskExecutionHistoryRepository } from "./TaskExecutionHistoryRepository";
import { TaskRepository } from "./TaskRepository";

/**
 * TaskExecutionHistoryService – Execution attempt tracking and lifecycle.
 * Each task rerun creates a new execution history record with incremented attempt_number.
 * Snapshots task input at execution time.
 */
export class TaskExecutionHistoryService {
  constructor(
    private readonly executionHistoryRepo: TaskExecutionHistoryRepository,
    private readonly taskRepo: TaskRepository
  ) {}

  /**
   * Create a new execution history record for a task.
   * - Gets the next attempt_number (max + 1)
   * - Snapshots the current task input
   * - Sets executionStatus = Running
   * - Updates Task.latestExecutionId
   */
  async createExecution(taskId: string, em?: EntityManager): Promise<TaskExecutionHistoryEntity> {
    const task = await this.taskRepo.findById(taskId, em);
    if (!task) throw new NotFoundError("Task", taskId);

    // Determine next attempt number
    const maxAttempt = await this.executionHistoryRepo.getMaxAttemptNumber(taskId, em);
    const nextAttempt = maxAttempt + 1;

    // Create execution record
    const execution = new TaskExecutionHistoryEntity();
    execution.taskId = taskId;
    execution.attemptNumber = nextAttempt;
    execution.executionStatus = "Running";
    execution.inputSnapshotJson = task.inputParametersJson;
    execution.resultSummaryJson = null;
    execution.resultLogs = null;
    execution.startTime = new Date();
    execution.endTime = null;

    const saved = await this.executionHistoryRepo.save(execution, em);

    // Update Task.latestExecutionId
    await this.taskRepo.updateTask(task, { latestExecutionId: saved.id }, em);

    return saved;
  }

  /**
   * Retrieve all execution attempts for a task, ordered by attempt_number.
   */
  async findByTaskId(taskId: string, em?: EntityManager): Promise<TaskExecutionHistoryEntity[]> {
    return this.executionHistoryRepo.findByTaskId(taskId, em);
  }

  /**
   * Retrieve the latest execution attempt for a task.
   */
  async findLatest(taskId: string, em?: EntityManager): Promise<TaskExecutionHistoryEntity | null> {
    return this.executionHistoryRepo.findLatestByTaskId(taskId, em);
  }

  /**
   * Mark an execution as complete with result.
   * Sets executionStatus, resultSummaryJson, resultLogs, and endTime.
   */
  async completeExecution(
    executionId: string,
    executionStatus: ExecutionStatus,
    resultSummaryJson?: string,
    resultLogs?: string,
    em?: EntityManager
  ): Promise<TaskExecutionHistoryEntity> {
    const execution = await this.executionHistoryRepo.findById(executionId, em);
    if (!execution) throw new NotFoundError("TaskExecutionHistory", executionId);

    execution.executionStatus = executionStatus;
    if (resultSummaryJson !== undefined) execution.resultSummaryJson = resultSummaryJson;
    if (resultLogs !== undefined) execution.resultLogs = resultLogs;
    execution.endTime = new Date();

    return this.executionHistoryRepo.save(execution, em);
  }
}
