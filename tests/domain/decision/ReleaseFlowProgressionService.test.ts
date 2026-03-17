import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";
import { DataSource } from "typeorm";
import { ReleaseFlowProgressionService } from "../../../src/domain/decision/ReleaseFlowProgressionService";
import { ReleaseFlowService } from "../../../src/domain/releaseflow/ReleaseFlowService";
import { ReleaseFlowRepository } from "../../../src/domain/releaseflow/ReleaseFlowRepository";
import { RequestService } from "../../../src/domain/releaseflow/RequestService";
import { RequestRepository } from "../../../src/domain/releaseflow/RequestRepository";
import { TaskRepository } from "../../../src/domain/task/TaskRepository";
import {
  createAndInitTestDataSource,
  clearAllTables,
  seedReleaseFlow,
  seedRequest,
  seedTask,
} from "../../helpers/testDataSource";

describe("ReleaseFlowProgressionService", () => {
  let ds: DataSource;
  let flowRepo: ReleaseFlowRepository;
  let requestRepo: RequestRepository;
  let taskRepo: TaskRepository;
  let flowService: ReleaseFlowService;
  let requestService: RequestService;
  let progressionService: ReleaseFlowProgressionService;

  beforeAll(async () => {
    ds = await createAndInitTestDataSource();
    flowRepo = new ReleaseFlowRepository(ds);
    requestRepo = new RequestRepository(ds);
    taskRepo = new TaskRepository(ds);
    flowService = new ReleaseFlowService(ds, flowRepo, requestRepo);
    requestService = new RequestService(ds, requestRepo);
    progressionService = new ReleaseFlowProgressionService(
      taskRepo,
      requestRepo,
      requestService,
      flowService,
      flowRepo
    );
  });

  afterAll(async () => {
    await ds.destroy();
  });

  afterEach(async () => {
    await clearAllTables(ds);
  });

  describe("progressAfterDecision", () => {
    it("completes request when all tasks are terminal (Approved/Skipped)", async () => {
      const rf = await seedReleaseFlow(ds, { currentStage: "SIT" });
      const req = await seedRequest(ds, rf.id, { stage: "SIT" });
      const task1 = await seedTask(ds, req.id, { taskStatus: "Approved" });
      await seedTask(ds, req.id, { taskStatus: "Skipped" });

      await progressionService.progressAfterDecision(task1.id);

      const updatedRequest = await requestRepo.findById(req.id);
      expect(updatedRequest?.requestStatus).toBe("Completed");
    });

    it("completes flow when in PROD and request is completed", async () => {
      const rf = await seedReleaseFlow(ds, { currentStage: "PROD" });
      const req = await seedRequest(ds, rf.id, { stage: "PROD" });
      const task = await seedTask(ds, req.id, { taskStatus: "Approved" });

      await progressionService.progressAfterDecision(task.id);

      const updatedFlow = await flowRepo.findById(rf.id);
      expect(updatedFlow?.flowStatus).toBe("Completed");
    });

    it("advances flow to next stage when request completed in SIT", async () => {
      const rf = await seedReleaseFlow(ds, { currentStage: "SIT" });
      const req = await seedRequest(ds, rf.id, { stage: "SIT" });
      const task = await seedTask(ds, req.id, { taskStatus: "Approved" });

      await progressionService.progressAfterDecision(task.id);

      const updatedFlow = await flowRepo.findById(rf.id);
      expect(updatedFlow?.currentStage).toBe("UAT");
    });

    it("advances from UAT to PROD", async () => {
      const rf = await seedReleaseFlow(ds, { currentStage: "UAT" });
      const req = await seedRequest(ds, rf.id, { stage: "UAT" });
      const task = await seedTask(ds, req.id, { taskStatus: "Approved" });

      await progressionService.progressAfterDecision(task.id);

      const updatedFlow = await flowRepo.findById(rf.id);
      expect(updatedFlow?.currentStage).toBe("PROD");
    });

    it("auto-readies next Pending task when not all tasks are terminal", async () => {
      const rf = await seedReleaseFlow(ds, { currentStage: "SIT" });
      const req = await seedRequest(ds, rf.id, { stage: "SIT" });
      const task1 = await seedTask(ds, req.id, { taskStatus: "Approved", taskName: "task-1" });
      const task2 = await seedTask(ds, req.id, { taskStatus: "Pending", taskName: "task-2" });

      await progressionService.progressAfterDecision(task1.id);

      const updatedTask2 = await taskRepo.findById(task2.id);
      expect(updatedTask2?.taskStatus).toBe("Ready_For_Execution");
    });

    it("doesn't auto-ready if not Pending", async () => {
      const rf = await seedReleaseFlow(ds, { currentStage: "SIT" });
      const req = await seedRequest(ds, rf.id, { stage: "SIT" });
      const task1 = await seedTask(ds, req.id, { taskStatus: "Approved", taskName: "task-1" });
      const task2Original = await seedTask(ds, req.id, {
        taskStatus: "Ready_For_Execution",
        taskName: "task-2",
      });

      await progressionService.progressAfterDecision(task1.id);

      const tasks = await taskRepo.findByRequestId(req.id);
      const task2 = tasks.find((t) => t.id === task2Original.id);
      expect(task2?.taskStatus).toBe("Ready_For_Execution");
    });

    it("handles multiple Pending tasks – readies only the first", async () => {
      const rf = await seedReleaseFlow(ds, { currentStage: "SIT" });
      const req = await seedRequest(ds, rf.id, { stage: "SIT" });
      const task1 = await seedTask(ds, req.id, { taskStatus: "Approved", taskName: "task-1" });
      const task2 = await seedTask(ds, req.id, { taskStatus: "Pending", taskName: "task-2" });
      const task3 = await seedTask(ds, req.id, { taskStatus: "Pending", taskName: "task-3" });

      await progressionService.progressAfterDecision(task1.id);

      const updatedTask2 = await taskRepo.findById(task2.id);
      const updatedTask3 = await taskRepo.findById(task3.id);
      // First pending is readied
      expect(updatedTask2?.taskStatus).toBe("Ready_For_Execution");
      // Others remain Pending
      expect(updatedTask3?.taskStatus).toBe("Pending");
    });
  });
});
