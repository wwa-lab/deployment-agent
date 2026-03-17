import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";
import { DataSource } from "typeorm";
import { ReleaseFlowRepository } from "../../../src/domain/releaseflow/ReleaseFlowRepository";
import { RequestRepository } from "../../../src/domain/releaseflow/RequestRepository";
import { ReleaseFlowService } from "../../../src/domain/releaseflow/ReleaseFlowService";
import { RequestService } from "../../../src/domain/releaseflow/RequestService";
import { NotFoundError } from "../../../src/errors/AppError";
import {
  createAndInitTestDataSource,
  clearAllTables,
  seedReleaseFlow,
  seedRequest,
  seedTask,
} from "../../helpers/testDataSource";

describe("ReleaseFlowService", () => {
  let ds: DataSource;
  let rfRepo: ReleaseFlowRepository;
  let reqRepo: RequestRepository;
  let rfService: ReleaseFlowService;

  beforeAll(async () => {
    ds = await createAndInitTestDataSource();
    rfRepo = new ReleaseFlowRepository(ds);
    reqRepo = new RequestRepository(ds);
    rfService = new ReleaseFlowService(ds, rfRepo, reqRepo);
  });

  afterAll(async () => {
    await ds.destroy();
  });

  afterEach(async () => {
    await clearAllTables(ds);
  });

  describe("create", () => {
    it("creates a Release Flow with Pending status", async () => {
      const rf = await rfService.create({
        projectId: "MY_PROJECT",
        projectName: "My Project",
        releaseId: "sit-my-project-0001",
        normalizedReleaseId: "sit-my-project-0001",
        firstStage: "SIT",
      });

      expect(rf.id).toBeDefined();
      expect(rf.projectId).toBe("MY_PROJECT");
      expect(rf.projectName).toBe("My Project");
      expect(rf.flowStatus).toBe("Pending");
      expect(rf.currentStage).toBe("SIT");
      expect(rf.reviewStatus).toBe("Pending_Review");
    });
  });

  describe("getById", () => {
    it("retrieves an existing Release Flow", async () => {
      const created = await seedReleaseFlow(ds);
      const found = await rfService.getById(created.id);
      expect(found.id).toBe(created.id);
    });

    it("throws NotFoundError for unknown ID", async () => {
      await expect(rfService.getById("non-existent")).rejects.toBeInstanceOf(
        NotFoundError
      );
    });
  });

  describe("findByGroupKey", () => {
    it("returns null when no matching Release Flow", async () => {
      const found = await rfService.findByGroupKey("PROJECT", "normalized_id");
      expect(found).toBeNull();
    });

    it("returns the matching Release Flow by projectId + normalizedReleaseId", async () => {
      await seedReleaseFlow(ds, {
        projectId: "ALPHA",
        projectName: "Alpha Project",
        normalizedReleaseId: "sit-alpha-0001",
      });
      const found = await rfService.findByGroupKey("ALPHA", "sit-alpha-0001");
      expect(found).not.toBeNull();
      expect(found?.projectId).toBe("ALPHA");
      expect(found?.projectName).toBe("Alpha Project");
    });
  });

  describe("list with filters", () => {
    it("returns paginated list", async () => {
      await seedReleaseFlow(ds, { projectId: "P1", projectName: "Project 1" });
      await seedReleaseFlow(ds, {
        projectId: "P2",
        projectName: "Project 2",
        normalizedReleaseId: "sit-p2-0001",
      });

      const result = await rfService.list({}, { page: 0, size: 10 });
      expect(result.total).toBe(2);
      expect(result.data).toHaveLength(2);
    });

    it("filters by projectId", async () => {
      await seedReleaseFlow(ds, { projectId: "P1", projectName: "Project 1" });
      await seedReleaseFlow(ds, {
        projectId: "P2",
        projectName: "Project 2",
        normalizedReleaseId: "sit-p2-0001",
      });

      const result = await rfService.list({ projectId: "P1" }, { page: 0, size: 10 });
      expect(result.total).toBe(1);
      expect(result.data[0].projectId).toBe("P1");
    });
  });

  describe("recomputeAndPersistStatus", () => {
    it("aggregates task statuses to update request and flow status", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      await seedTask(ds, req.id, { taskStatus: "Approved" });
      await seedTask(ds, req.id, {
        taskStatus: "Skipped",
        taskGroupId: "TG-001",
        stepSeq: 2,
      });

      await rfService.recomputeAndPersistStatus(rf.id);

      const updated = await rfService.getById(rf.id);
      expect(updated.flowStatus).toBe("Completed");
    });

    it("sets flow status to Running when a task is Executing", async () => {
      const rf = await seedReleaseFlow(ds);
      const req = await seedRequest(ds, rf.id);
      await seedTask(ds, req.id, { taskStatus: "Executing" });

      await rfService.recomputeAndPersistStatus(rf.id);

      const updated = await rfService.getById(rf.id);
      expect(updated.flowStatus).toBe("Running");
    });

    it("throws NotFoundError for unknown release flow", async () => {
      await expect(
        rfService.recomputeAndPersistStatus("no-such-id")
      ).rejects.toBeInstanceOf(NotFoundError);
    });

    it("correctly handles multi-stage flow: SIT Completed + UAT Running → Running overall", async () => {
      const rf = await seedReleaseFlow(ds, { currentStage: "UAT" });
      // SIT request – all tasks done
      const sitReq = await seedRequest(ds, rf.id, { stage: "SIT" });
      await seedTask(ds, sitReq.id, { taskStatus: "Approved", taskGroupId: "SIT-TG-001", stepSeq: 1 });

      // UAT request – one task still executing
      const uatReq = await seedRequest(ds, rf.id, { stage: "UAT" });
      await seedTask(ds, uatReq.id, { taskStatus: "Executing", taskGroupId: "UAT-TG-001", stepSeq: 1 });

      await rfService.recomputeAndPersistStatus(rf.id);

      const updated = await rfService.getById(rf.id);
      expect(updated.flowStatus).toBe("Running");
    });

    it("sets flow to Completed only when ALL active stages are Completed", async () => {
      const rf = await seedReleaseFlow(ds, { currentStage: "UAT" });

      const sitReq = await seedRequest(ds, rf.id, { stage: "SIT" });
      await seedTask(ds, sitReq.id, { taskStatus: "Approved", taskGroupId: "SIT-TG-001", stepSeq: 1 });

      const uatReq = await seedRequest(ds, rf.id, { stage: "UAT" });
      await seedTask(ds, uatReq.id, { taskStatus: "Approved", taskGroupId: "UAT-TG-001", stepSeq: 1 });

      await rfService.recomputeAndPersistStatus(rf.id);

      const updated = await rfService.getById(rf.id);
      expect(updated.flowStatus).toBe("Completed");
    });
  });

  describe("advanceStage", () => {
    it("advances from SIT to UAT", async () => {
      const rf = await seedReleaseFlow(ds, { currentStage: "SIT" });

      await rfService.advanceStage(rf.id);

      const updated = await rfService.getById(rf.id);
      expect(updated.currentStage).toBe("UAT");
    });

    it("advances from UAT to PROD", async () => {
      const rf = await seedReleaseFlow(ds, {
        currentStage: "UAT",
        normalizedReleaseId: "sit-p-uat-0001",
      });

      await rfService.advanceStage(rf.id);

      const updated = await rfService.getById(rf.id);
      expect(updated.currentStage).toBe("PROD");
    });

    it("does not advance past PROD", async () => {
      const rf = await seedReleaseFlow(ds, {
        currentStage: "PROD",
        normalizedReleaseId: "sit-p-prod-0001",
      });

      await rfService.advanceStage(rf.id);

      const updated = await rfService.getById(rf.id);
      expect(updated.currentStage).toBe("PROD"); // unchanged
    });
  });
});

describe("RequestService", () => {
  let ds: DataSource;
  let reqRepo: RequestRepository;
  let reqService: RequestService;

  beforeAll(async () => {
    ds = await createAndInitTestDataSource();
    reqRepo = new RequestRepository(ds);
    reqService = new RequestService(ds, reqRepo);
  });

  afterAll(async () => {
    await ds.destroy();
  });

  afterEach(async () => {
    await clearAllTables(ds);
  });

  it("creates a Request in Pending status", async () => {
    const rf = await seedReleaseFlow(ds);
    const req = await reqService.create({ releaseFlowId: rf.id, stage: "SIT" });
    expect(req.id).toBeDefined();
    expect(req.stage).toBe("SIT");
    expect(req.requestStatus).toBe("Pending");
  });

  it("throws NotFoundError for unknown Request id", async () => {
    await expect(reqService.getById("no-such")).rejects.toBeInstanceOf(NotFoundError);
  });

  it("lists Requests for a Release Flow", async () => {
    const rf = await seedReleaseFlow(ds);
    await reqService.create({ releaseFlowId: rf.id, stage: "SIT" });
    await reqService.create({ releaseFlowId: rf.id, stage: "UAT" });

    const requests = await reqService.listByReleaseFlow(rf.id);
    expect(requests).toHaveLength(2);
  });

  it("finds Request by stage", async () => {
    const rf = await seedReleaseFlow(ds);
    await reqService.create({ releaseFlowId: rf.id, stage: "SIT" });

    const found = await reqService.findByStage(rf.id, "SIT");
    expect(found).not.toBeNull();
    expect(found?.stage).toBe("SIT");
  });

  it("updates Request status", async () => {
    const rf = await seedReleaseFlow(ds);
    const req = await reqService.create({ releaseFlowId: rf.id, stage: "SIT" });

    await reqService.updateStatus(req.id, "Running");

    const updated = await reqService.getById(req.id);
    expect(updated.requestStatus).toBe("Running");
  });
});
