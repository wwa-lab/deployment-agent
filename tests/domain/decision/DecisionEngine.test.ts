import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";
import { DataSource } from "typeorm";
import { DecisionEngine } from "../../../src/domain/decision/DecisionEngine";
import { TaskService } from "../../../src/domain/task/TaskService";
import { TaskExecutionHistoryService } from "../../../src/domain/task/TaskExecutionHistoryService";
import { TaskRepository } from "../../../src/domain/task/TaskRepository";
import { TaskExecutionHistoryRepository } from "../../../src/domain/task/TaskExecutionHistoryRepository";
import { AuditLoggerService } from "../../../src/domain/audit/AuditLoggerService";
import { AuditLogRepository } from "../../../src/domain/audit/AuditLogRepository";
import { ForbiddenError, InvalidStateTransitionError } from "../../../src/errors/AppError";
import {
  createAndInitTestDataSource,
  clearAllTables,
  seedReleaseFlow,
  seedRequest,
  seedTask,
} from "../../helpers/testDataSource";
import { UserContext } from "../../../src/contracts/UserContext";

describe("DecisionEngine", () => {
  let ds: DataSource;
  let taskRepo: TaskRepository;
  let executionHistoryRepo: TaskExecutionHistoryRepository;
  let auditLogRepo: AuditLogRepository;
  let taskService: TaskService;
  let executionHistoryService: TaskExecutionHistoryService;
  let auditLogger: AuditLoggerService;
  let decisionEngine: DecisionEngine;

  let tlUser: UserContext;
  let devUser: UserContext;

  beforeAll(async () => {
    ds = await createAndInitTestDataSource();
    taskRepo = new TaskRepository(ds);
    executionHistoryRepo = new TaskExecutionHistoryRepository(ds);
    auditLogRepo = new AuditLogRepository(ds);
    auditLogger = new AuditLoggerService(auditLogRepo);
    taskService = new TaskService(taskRepo, auditLogger);
    executionHistoryService = new TaskExecutionHistoryService(executionHistoryRepo, taskRepo);
    decisionEngine = new DecisionEngine(taskService, executionHistoryService, auditLogger, ds);

    tlUser = { userId: "tl-user", role: "TL" };
    devUser = { userId: "dev-user", role: "DEVELOPER" };
  });

  afterAll(async () => {
    await ds.destroy();
  });

  afterEach(async () => {
    await clearAllTables(ds);
  });

  describe("applyDecision - approve", () => {
    it("allows Awaiting_Review → Approved for TL", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      const task = await seedTask(ds, req.id, { taskStatus: "Awaiting_Review" });

      await decisionEngine.applyDecision({
        taskId: task.id,
        decision: "approve",
        user: tlUser,
        comment: "Looks good",
      });

      const updated = await taskRepo.findById(task.id);
      expect(updated?.taskStatus).toBe("Approved");
    });

    it("throws ForbiddenError for non-TL approving", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      const task = await seedTask(ds, req.id, { taskStatus: "Awaiting_Review" });

      await expect(
        decisionEngine.applyDecision({
          taskId: task.id,
          decision: "approve",
          user: devUser,
        })
      ).rejects.toBeInstanceOf(ForbiddenError);
    });

    it("throws InvalidStateTransitionError when not in Awaiting_Review", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      const task = await seedTask(ds, req.id, { taskStatus: "Pending" });

      await expect(
        decisionEngine.applyDecision({
          taskId: task.id,
          decision: "approve",
          user: tlUser,
        })
      ).rejects.toBeInstanceOf(InvalidStateTransitionError);
    });
  });

  describe("applyDecision - reject", () => {
    it("allows Awaiting_Review → Rejected for TL", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      const task = await seedTask(ds, req.id, { taskStatus: "Awaiting_Review" });

      await decisionEngine.applyDecision({
        taskId: task.id,
        decision: "reject",
        user: tlUser,
        comment: "Needs rework",
      });

      const updated = await taskRepo.findById(task.id);
      expect(updated?.taskStatus).toBe("Rejected");
    });

    it("throws InvalidStateTransitionError when not in Awaiting_Review", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      const task = await seedTask(ds, req.id, { taskStatus: "Pending" });

      await expect(
        decisionEngine.applyDecision({
          taskId: task.id,
          decision: "reject",
          user: tlUser,
        })
      ).rejects.toBeInstanceOf(InvalidStateTransitionError);
    });
  });

  describe("applyDecision - rerun", () => {
    it("allows Rejected → Ready_For_Execution and creates new execution", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      const task = await seedTask(ds, req.id, { taskStatus: "Rejected" });

      await decisionEngine.applyDecision({
        taskId: task.id,
        decision: "rerun",
        user: tlUser,
        comment: "Retrying",
      });

      const updated = await taskRepo.findById(task.id);
      expect(updated?.taskStatus).toBe("Ready_For_Execution");
      expect(updated?.latestExecutionId).toBeDefined();
    });

    it("allows Failed → Ready_For_Execution and creates new execution", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      const task = await seedTask(ds, req.id, { taskStatus: "Failed" });

      await decisionEngine.applyDecision({
        taskId: task.id,
        decision: "rerun",
        user: tlUser,
      });

      const updated = await taskRepo.findById(task.id);
      expect(updated?.taskStatus).toBe("Ready_For_Execution");
      expect(updated?.latestExecutionId).toBeDefined();
    });

    it("throws InvalidStateTransitionError when not in Rejected/Failed", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      const task = await seedTask(ds, req.id, { taskStatus: "Pending" });

      await expect(
        decisionEngine.applyDecision({
          taskId: task.id,
          decision: "rerun",
          user: tlUser,
        })
      ).rejects.toBeInstanceOf(InvalidStateTransitionError);
    });
  });

  describe("applyDecision - skip", () => {
    it("allows Pending → Skipped for TL", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      const task = await seedTask(ds, req.id, { taskStatus: "Pending" });

      await decisionEngine.applyDecision({
        taskId: task.id,
        decision: "skip",
        user: tlUser,
        comment: "Not needed",
      });

      const updated = await taskRepo.findById(task.id);
      expect(updated?.taskStatus).toBe("Skipped");
    });

    it("allows Ready_For_Execution → Skipped for TL", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      const task = await seedTask(ds, req.id, { taskStatus: "Ready_For_Execution" });

      await decisionEngine.applyDecision({
        taskId: task.id,
        decision: "skip",
        user: tlUser,
      });

      const updated = await taskRepo.findById(task.id);
      expect(updated?.taskStatus).toBe("Skipped");
    });

    it("throws InvalidStateTransitionError when not in Pending/Ready_For_Execution", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      const task = await seedTask(ds, req.id, { taskStatus: "Approved" });

      await expect(
        decisionEngine.applyDecision({
          taskId: task.id,
          decision: "skip",
          user: tlUser,
        })
      ).rejects.toBeInstanceOf(InvalidStateTransitionError);
    });
  });

  describe("auditLogging", () => {
    it("audits all decision types", async () => {
      const decisions: Array<{ decision: string; initialStatus: string }> = [
        { decision: "approve", initialStatus: "Awaiting_Review" },
        { decision: "reject", initialStatus: "Awaiting_Review" },
        { decision: "skip", initialStatus: "Pending" },
      ];

      for (const { decision, initialStatus } of decisions) {
        await clearAllTables(ds);

        const rf = await seedReleaseFlow(ds);
        const req = await seedRequest(ds, rf.id);
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const task = await seedTask(ds, req.id, { taskStatus: initialStatus as any });

        await decisionEngine.applyDecision({
          taskId: task.id,
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          decision: decision as any,
          user: tlUser,
        });

        const result = await auditLogRepo.findAll({ taskId: task.id }, { page: 0, size: 10 });
        const auditEntry = result.data.find((e) => e.actionType === decision);
        expect(auditEntry).toBeDefined();
        expect(auditEntry?.operatorId).toBe(tlUser.userId);
      }
    });
  });
});
