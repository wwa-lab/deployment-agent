import { EntityManager } from "typeorm";
import { ExecutionType, TaskStatus } from "../../contracts/enums";
import { UserContext } from "../../contracts/UserContext";
import { InvalidStateTransitionError, NotFoundError, ValidationError } from "../../errors/AppError";
import { AuditLoggerService } from "../audit/AuditLoggerService";
import { TaskEntity } from "./Task.entity";
import { TaskRepository } from "./TaskRepository";
import { isValidTaskTransition } from "./taskStateMachine";

/**
 * Input for creating a new Task (used by Import Service and tests).
 * All Excel-derived fields are explicit; only workflow fields are required.
 */
export interface CreateTaskInput {
  requestId: string;
  taskGroupId: string;
  taskGroupName: string;
  stepSeq: number;
  taskName: string;
  executionType: ExecutionType;
  inputJson?: string | null;
  expectedOutput?: string | null;
  owner?: string | null;
  plannedStartTime?: Date | null;
  plannedEndTime?: Date | null;
  importMetadataJson?: string | null;
}

/**
 * TaskService – Task CRUD and lifecycle management.
 * Enforces state machine transitions and audits all state changes.
 * Handles optimistic locking via TaskRepository.updateTask pattern.
 */
export class TaskService {
  constructor(
    private readonly taskRepo: TaskRepository,
    private readonly auditLogger: AuditLoggerService
  ) {}

  /**
   * Retrieve a task by ID. Throws NotFoundError if not found.
   */
  async getById(taskId: string, em?: EntityManager): Promise<TaskEntity> {
    const task = await this.taskRepo.findById(taskId, em);
    if (!task) throw new NotFoundError("Task", taskId);
    return task;
  }

  /**
   * List all tasks for a given request ID, ordered by (taskGroupId, stepSeq).
   */
  async listByRequestId(requestId: string, em?: EntityManager): Promise<TaskEntity[]> {
    return this.taskRepo.findByRequestId(requestId, em);
  }

  /**
   * Create a new task in Pending status.
   * All required template-derived fields must be provided via CreateTaskInput.
   */
  async create(input: CreateTaskInput, em?: EntityManager): Promise<TaskEntity> {
    const task = new TaskEntity();
    task.requestId = input.requestId;
    task.taskGroupId = input.taskGroupId;
    task.taskGroupName = input.taskGroupName;
    task.stepSeq = input.stepSeq;
    task.taskName = input.taskName;
    task.executionType = input.executionType;
    task.taskStatus = "Pending";
    task.inputParametersJson = input.inputJson ?? null;
    task.expectedOutput = input.expectedOutput ?? null;
    task.owner = input.owner ?? null;
    task.plannedStartTime = input.plannedStartTime ?? null;
    task.plannedEndTime = input.plannedEndTime ?? null;
    task.importMetadataJson = input.importMetadataJson ?? null;
    task.currentResultSummaryJson = null;
    task.latestExecutionId = null;
    task.startTime = null;
    task.endTime = null;

    return this.taskRepo.save(task, em);
  }

  /**
   * Update task status with transition validation.
   * Throws InvalidStateTransitionError if transition is disallowed.
   * Honors @VersionColumn for optimistic locking.
   * Audits the transition.
   */
  async updateStatus(
    taskId: string,
    newStatus: TaskStatus,
    user: UserContext,
    comment?: string,
    em?: EntityManager
  ): Promise<TaskEntity> {
    const task = await this.getById(taskId, em);

    if (!isValidTaskTransition(task.taskStatus, newStatus)) {
      throw new InvalidStateTransitionError(task.taskStatus, newStatus, "Task");
    }

    await this.taskRepo.updateTask(task, { taskStatus: newStatus }, em);

    // Audit the transition
    await this.auditLogger.log(
      {
        user,
        actionType: "edit",
        taskId,
        requestId: task.requestId,
        context: {
          transitionFrom: task.taskStatus,
          transitionTo: newStatus,
          comment,
        },
      },
      em
    );

    task.taskStatus = newStatus;
    return task;
  }

  /**
   * Edit task input parameters (only allowed in Pending or Ready_For_Execution).
   * Validates JSON format and audits the change.
   */
  async editInput(
    taskId: string,
    newInputJson: string,
    user: UserContext,
    em?: EntityManager
  ): Promise<TaskEntity> {
    const task = await this.getById(taskId, em);

    // Validate state
    if (task.taskStatus !== "Pending" && task.taskStatus !== "Ready_For_Execution") {
      throw new ValidationError(
        `Task input can only be edited in Pending or Ready_For_Execution states. Current state: ${task.taskStatus}`
      );
    }

    // Validate JSON
    try {
      JSON.parse(newInputJson);
    } catch (err) {
      throw new ValidationError("Invalid JSON format for task input", { parseError: String(err) });
    }

    await this.taskRepo.updateTask(task, { inputParametersJson: newInputJson }, em);

    // Audit the edit
    await this.auditLogger.log(
      {
        user,
        actionType: "edit",
        taskId,
        requestId: task.requestId,
        context: {
          fieldChanged: "inputParameters",
          oldValue: task.inputParametersJson,
          newValue: newInputJson,
        },
      },
      em
    );

    task.inputParametersJson = newInputJson;
    return task;
  }

  /**
   * Update the result metadata for a task.
   * Sets currentResultSummaryJson and latestExecutionId.
   */
  async updateResultMetadata(
    taskId: string,
    resultSummaryJson: string,
    executionId: string,
    em?: EntityManager
  ): Promise<TaskEntity> {
    const task = await this.getById(taskId, em);

    await this.taskRepo.updateTask(
      task,
      {
        currentResultSummaryJson: resultSummaryJson,
        latestExecutionId: executionId,
      },
      em
    );

    task.currentResultSummaryJson = resultSummaryJson;
    task.latestExecutionId = executionId;
    return task;
  }
}
