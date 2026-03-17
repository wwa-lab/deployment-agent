import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";
import { DataSource } from "typeorm";
import { TaskService } from "../../../src/domain/task/TaskService";
import { TaskRepository } from "../../../src/domain/task/TaskRepository";
import { AuditLoggerService } from "../../../src/domain/audit/AuditLoggerService";
import { AuditLogRepository } from "../../../src/domain/audit/AuditLogRepository";
import { InvalidStateTransitionError, NotFoundError, ValidationError } from "../../../src/errors/AppError";
import {
  createAndInitTestDataSource,
  clearAllTables,
  seedReleaseFlow,
  seedRequest,
  seedTask,
} from "../../helpers/testDataSource";
import { UserContext } from "../../../src/contracts/UserContext";

describe("TaskService", () => {
  let ds: DataSource;
  let taskRepo: TaskRepository;
  let auditLogRepo: AuditLogRepository;
  let auditLogger: AuditLoggerService;
  let taskService: TaskService;
  let testUser: UserContext;

  beforeAll(async () => {
    ds = await createAndInitTestDataSource();
    taskRepo = new TaskRepository(ds);
    auditLogRepo = new AuditLogRepository(ds);
    auditLogger = new AuditLoggerService(auditLogRepo);
    taskService = new TaskService(taskRepo, auditLogger);

    testUser = {
      userId: "test-user-1",
      role: "TL",
    };
  });

  afterAll(async () => {
    await ds.destroy();
  });

  afterEach(async () => {
    await clearAllTables(ds);
  });

  describe("create", () => {
    it("creates a task in Pending status with all required fields", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);

      const inputJson = JSON.stringify({ script: "deploy.sh", parameters: "--env staging" });
      const task = await taskService.create({
        requestId: req.id,
        taskGroupId: "TG-001",
        taskGroupName: "Deploy App",
        stepSeq: 1,
        taskName: "deploy-app",
        executionType: "AUTO",
        inputJson,
        expectedOutput: "Deployment successful",
        owner: "alice",
      });

      expect(task.id).toBeDefined();
      expect(task.requestId).toBe(req.id);
      expect(task.taskGroupId).toBe("TG-001");
      expect(task.taskGroupName).toBe("Deploy App");
      expect(task.stepSeq).toBe(1);
      expect(task.taskName).toBe("deploy-app");
      expect(task.executionType).toBe("AUTO");
      expect(task.taskStatus).toBe("Pending");
      expect(task.inputParametersJson).toBe(inputJson);
      expect(task.expectedOutput).toBe("Deployment successful");
      expect(task.owner).toBe("alice");
      expect(task.currentResultSummaryJson).toBeNull();
      expect(task.latestExecutionId).toBeNull();
    });

    it("creates a MANUAL task", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);

      const task = await taskService.create({
        requestId: req.id,
        taskGroupId: "TG-002",
        taskGroupName: "Manual Check",
        stepSeq: 1,
        taskName: "manual-smoke-test",
        executionType: "MANUAL",
      });

      expect(task.executionType).toBe("MANUAL");
      expect(task.taskStatus).toBe("Pending");
      expect(task.inputParametersJson).toBeNull();
    });

    it("creates a task with null optional fields if not provided", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);

      const task = await taskService.create({
        requestId: req.id,
        taskGroupId: "TG-003",
        taskGroupName: "Check Status",
        stepSeq: 1,
        taskName: "check-status",
        executionType: "AUTO",
      });

      expect(task.inputParametersJson).toBeNull();
      expect(task.expectedOutput).toBeNull();
      expect(task.owner).toBeNull();
      expect(task.plannedStartTime).toBeNull();
      expect(task.plannedEndTime).toBeNull();
      expect(task.importMetadataJson).toBeNull();
    });
  });

  describe("getById", () => {
    it("retrieves an existing task", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      const created = await seedTask(ds, req.id);

      const found = await taskService.getById(created.id);
      expect(found.id).toBe(created.id);
      expect(found.taskName).toBe(created.taskName);
    });

    it("throws NotFoundError for unknown task ID", async () => {
      await expect(taskService.getById("non-existent")).rejects.toBeInstanceOf(NotFoundError);
    });
  });

  describe("listByRequestId", () => {
    it("lists all tasks for a request ordered by (taskGroupId, stepSeq)", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);

      // Seed in reverse order to verify ordering is correct
      const task2 = await seedTask(ds, req.id, {
        taskGroupId: "TG-001",
        stepSeq: 2,
        taskName: "task-step-2",
      });
      const task1 = await seedTask(ds, req.id, {
        taskGroupId: "TG-001",
        stepSeq: 1,
        taskName: "task-step-1",
      });

      const tasks = await taskService.listByRequestId(req.id);
      expect(tasks).toHaveLength(2);
      // step 1 must come before step 2
      expect(tasks[0].id).toBe(task1.id);
      expect(tasks[1].id).toBe(task2.id);
    });

    it("returns empty array if no tasks", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);

      const tasks = await taskService.listByRequestId(req.id);
      expect(tasks).toHaveLength(0);
    });
  });

  describe("updateStatus", () => {
    it("allows valid transition Pending → Ready_For_Execution", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      const task = await seedTask(ds, req.id, { taskStatus: "Pending" });

      const updated = await taskService.updateStatus(task.id, "Ready_For_Execution", testUser);

      expect(updated.taskStatus).toBe("Ready_For_Execution");
    });

    it("allows valid transition Ready_For_Execution → Executing", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      const task = await seedTask(ds, req.id, { taskStatus: "Ready_For_Execution" });

      const updated = await taskService.updateStatus(task.id, "Executing", testUser);

      expect(updated.taskStatus).toBe("Executing");
    });

    it("allows valid transition Executing → Awaiting_Review", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      const task = await seedTask(ds, req.id, { taskStatus: "Executing" });

      const updated = await taskService.updateStatus(task.id, "Awaiting_Review", testUser);

      expect(updated.taskStatus).toBe("Awaiting_Review");
    });

    it("allows valid transition Awaiting_Review → Approved", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      const task = await seedTask(ds, req.id, { taskStatus: "Awaiting_Review" });

      const updated = await taskService.updateStatus(task.id, "Approved", testUser);

      expect(updated.taskStatus).toBe("Approved");
    });

    it("allows rerun transition Rejected → Ready_For_Execution", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      const task = await seedTask(ds, req.id, { taskStatus: "Rejected" });

      const updated = await taskService.updateStatus(task.id, "Ready_For_Execution", testUser);

      expect(updated.taskStatus).toBe("Ready_For_Execution");
    });

    it("allows rerun transition Failed → Ready_For_Execution", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      const task = await seedTask(ds, req.id, { taskStatus: "Failed" });

      const updated = await taskService.updateStatus(task.id, "Ready_For_Execution", testUser);

      expect(updated.taskStatus).toBe("Ready_For_Execution");
    });

    it("throws InvalidStateTransitionError for disallowed transition", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      const task = await seedTask(ds, req.id, { taskStatus: "Pending" });

      await expect(
        taskService.updateStatus(task.id, "Approved", testUser)
      ).rejects.toBeInstanceOf(InvalidStateTransitionError);
    });

    it("creates audit log entry for status transition", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      const task = await seedTask(ds, req.id, { taskStatus: "Pending" });

      await taskService.updateStatus(task.id, "Ready_For_Execution", testUser);

      const result = await auditLogRepo.findAll({ taskId: task.id }, { page: 0, size: 10 });
      expect(result.data.length).toBeGreaterThan(0);
      const entry = result.data[0];
      expect(entry.actionType).toBe("edit");
      expect(entry.operatorId).toBe(testUser.userId);
    });

    it("@VersionColumn increments on each successful save (optimistic lock wiring)", async () => {
      // Verifies that the @VersionColumn mechanism is wired correctly.
      // Oracle enforces the version mismatch at the driver level (throws
      // OptimisticLockVersionMismatchError on stale saves). The sql.js
      // in-memory driver used in tests does not enforce this constraint,
      // so this test instead validates the version field increments correctly,
      // confirming the column is present and managed by TypeORM.
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      const task = await seedTask(ds, req.id, { taskStatus: "Pending" });

      const v0 = task.version;

      const afterFirst = await taskRepo.updateTask(task, { taskStatus: "Ready_For_Execution" });
      expect(afterFirst.version).toBe(v0 + 1);

      const afterSecond = await taskRepo.updateTask(afterFirst, { taskStatus: "Executing" });
      expect(afterSecond.version).toBe(v0 + 2);
    });
  });

  describe("editInput", () => {
    it("edits input in Pending state", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      const task = await seedTask(ds, req.id, {
        taskStatus: "Pending",
        inputParametersJson: JSON.stringify({ script: "v1.sh" }),
      });

      const newInput = JSON.stringify({ script: "v2.sh" });
      const updated = await taskService.editInput(task.id, newInput, testUser);

      expect(updated.inputParametersJson).toBe(newInput);
    });

    it("edits input in Ready_For_Execution state", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      const task = await seedTask(ds, req.id, {
        taskStatus: "Ready_For_Execution",
        inputParametersJson: JSON.stringify({ script: "v1.sh" }),
      });

      const newInput = JSON.stringify({ script: "v2.sh" });
      const updated = await taskService.editInput(task.id, newInput, testUser);

      expect(updated.inputParametersJson).toBe(newInput);
    });

    it("throws ValidationError when editing in Executing state", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      const task = await seedTask(ds, req.id, { taskStatus: "Executing" });

      const newInput = JSON.stringify({ script: "v2.sh" });

      await expect(taskService.editInput(task.id, newInput, testUser)).rejects.toBeInstanceOf(
        ValidationError
      );
    });

    it("throws ValidationError for invalid JSON", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      const task = await seedTask(ds, req.id, { taskStatus: "Pending" });

      await expect(
        taskService.editInput(task.id, "{ invalid json", testUser)
      ).rejects.toBeInstanceOf(ValidationError);
    });

    it("creates audit log entry for input edit", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      const task = await seedTask(ds, req.id, {
        taskStatus: "Pending",
        inputParametersJson: JSON.stringify({ script: "v1.sh" }),
      });

      const newInput = JSON.stringify({ script: "v2.sh" });
      await taskService.editInput(task.id, newInput, testUser);

      const result = await auditLogRepo.findAll({ taskId: task.id }, { page: 0, size: 10 });
      expect(result.data.length).toBeGreaterThan(0);
      const entry = result.data[0];
      expect(entry.actionType).toBe("edit");
    });
  });

  describe("updateResultMetadata", () => {
    it("updates result metadata correctly", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      const task = await seedTask(ds, req.id);
      const executionId = "exec-123";
      const resultJson = JSON.stringify({ status: "success", message: "Deployed" });

      const updated = await taskService.updateResultMetadata(
        task.id,
        resultJson,
        executionId
      );

      expect(updated.currentResultSummaryJson).toBe(resultJson);
      expect(updated.latestExecutionId).toBe(executionId);
    });

    it("throws NotFoundError for unknown task", async () => {
      const executionId = "exec-123";
      const resultJson = JSON.stringify({ status: "success" });

      await expect(
        taskService.updateResultMetadata("non-existent", resultJson, executionId)
      ).rejects.toBeInstanceOf(NotFoundError);
    });
  });
});
