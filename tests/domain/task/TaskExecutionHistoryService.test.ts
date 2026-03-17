import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";
import { DataSource } from "typeorm";
import { TaskExecutionHistoryService } from "../../../src/domain/task/TaskExecutionHistoryService";
import { TaskExecutionHistoryRepository } from "../../../src/domain/task/TaskExecutionHistoryRepository";
import { TaskRepository } from "../../../src/domain/task/TaskRepository";
import { NotFoundError } from "../../../src/errors/AppError";
import {
  createAndInitTestDataSource,
  clearAllTables,
  seedReleaseFlow,
  seedRequest,
  seedTask,
} from "../../helpers/testDataSource";

describe("TaskExecutionHistoryService", () => {
  let ds: DataSource;
  let executionHistoryRepo: TaskExecutionHistoryRepository;
  let taskRepo: TaskRepository;
  let service: TaskExecutionHistoryService;

  beforeAll(async () => {
    ds = await createAndInitTestDataSource();
    executionHistoryRepo = new TaskExecutionHistoryRepository(ds);
    taskRepo = new TaskRepository(ds);
    service = new TaskExecutionHistoryService(executionHistoryRepo, taskRepo);
  });

  afterAll(async () => {
    await ds.destroy();
  });

  afterEach(async () => {
    await clearAllTables(ds);
  });

  describe("createExecution", () => {
    it("creates first execution with attempt_number = 1", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      const task = await seedTask(ds, req.id, {
        inputParametersJson: JSON.stringify({ version: "1.0.0", app: "myapp" }),
      });

      const execution = await service.createExecution(task.id);

      expect(execution.id).toBeDefined();
      expect(execution.taskId).toBe(task.id);
      expect(execution.attemptNumber).toBe(1);
      expect(execution.executionStatus).toBe("Running");
      expect(execution.startTime).toBeDefined();
      expect(execution.endTime).toBeNull();
    });

    it("creates second execution with attempt_number = 2", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      const task = await seedTask(ds, req.id);

      const exec1 = await service.createExecution(task.id);
      const exec2 = await service.createExecution(task.id);

      expect(exec1.attemptNumber).toBe(1);
      expect(exec2.attemptNumber).toBe(2);
    });

    it("snapshots task input at execution time", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      const originalInput = JSON.stringify({ version: "1.0.0", env: "staging" });
      const task = await seedTask(ds, req.id, { inputParametersJson: originalInput });

      const execution = await service.createExecution(task.id);

      expect(execution.inputSnapshotJson).toBe(originalInput);
    });

    it("updates Task.latestExecutionId on creation", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      const task = await seedTask(ds, req.id);

      const execution = await service.createExecution(task.id);

      const reloaded = await taskRepo.findById(task.id);
      expect(reloaded?.latestExecutionId).toBe(execution.id);
    });

    it("throws NotFoundError for unknown task", async () => {
      await expect(service.createExecution("non-existent")).rejects.toBeInstanceOf(NotFoundError);
    });

    it("enforces unique (taskId, attemptNumber) at DB level", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      const task = await seedTask(ds, req.id);

      // Create and complete first execution
      const exec1 = await service.createExecution(task.id);
      await service.completeExecution(exec1.id, "Completed");

      // Create second execution (should succeed with attemptNumber = 2)
      const exec2 = await service.createExecution(task.id);
      expect(exec2.attemptNumber).toBe(2);
    });
  });

  describe("findByTaskId", () => {
    it("returns all executions for a task ordered by attempt", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      const task = await seedTask(ds, req.id);

      await service.createExecution(task.id);
      await service.createExecution(task.id);
      await service.createExecution(task.id);

      const executions = await service.findByTaskId(task.id);

      expect(executions).toHaveLength(3);
      expect(executions[0].attemptNumber).toBe(1);
      expect(executions[1].attemptNumber).toBe(2);
      expect(executions[2].attemptNumber).toBe(3);
    });

    it("returns empty array if no executions", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      const task = await seedTask(ds, req.id);

      const executions = await service.findByTaskId(task.id);

      expect(executions).toHaveLength(0);
    });
  });

  describe("findLatest", () => {
    it("returns the latest (highest attempt_number) execution", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      const task = await seedTask(ds, req.id);

      await service.createExecution(task.id);
      await service.createExecution(task.id);
      const exec3 = await service.createExecution(task.id);

      const latest = await service.findLatest(task.id);

      expect(latest?.id).toBe(exec3.id);
      expect(latest?.attemptNumber).toBe(3);
    });

    it("returns null if no executions", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      const task = await seedTask(ds, req.id);

      const latest = await service.findLatest(task.id);

      expect(latest).toBeNull();
    });
  });

  describe("completeExecution", () => {
    it("sets executionStatus, resultSummaryJson, and endTime", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      const task = await seedTask(ds, req.id);

      const execution = await service.createExecution(task.id);
      const resultJson = JSON.stringify({ deploymentId: "deploy-123", status: "Success" });
      const resultLogs = "Deployment started...\nAll checks passed.\n";

      const completed = await service.completeExecution(
        execution.id,
        "Completed",
        resultJson,
        resultLogs
      );

      expect(completed.executionStatus).toBe("Completed");
      expect(completed.resultSummaryJson).toBe(resultJson);
      expect(completed.resultLogs).toBe(resultLogs);
      expect(completed.endTime).toBeDefined();
      expect(completed.endTime).not.toBeNull();
    });

    it("completes with minimal fields (only status)", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      const task = await seedTask(ds, req.id);

      const execution = await service.createExecution(task.id);

      const completed = await service.completeExecution(execution.id, "Failed");

      expect(completed.executionStatus).toBe("Failed");
      expect(completed.endTime).toBeDefined();
    });

    it("throws NotFoundError for unknown execution", async () => {
      await expect(
        service.completeExecution("non-existent", "Completed")
      ).rejects.toBeInstanceOf(NotFoundError);
    });

    it("handles Timed_Out status", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      const task = await seedTask(ds, req.id);

      const execution = await service.createExecution(task.id);
      const completed = await service.completeExecution(execution.id, "Timed_Out");

      expect(completed.executionStatus).toBe("Timed_Out");
    });
  });
});
