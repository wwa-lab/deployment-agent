import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";
import { DataSource } from "typeorm";
import { AuditLogRepository } from "../../../src/domain/audit/AuditLogRepository";
import { AuditLoggerService } from "../../../src/domain/audit/AuditLoggerService";
import { UserContext } from "../../../src/contracts/UserContext";
import {
  createAndInitTestDataSource,
  clearAllTables,
} from "../../helpers/testDataSource";

const testUser: UserContext = {
  userId: "user-001",
  role: "TL",
  displayName: "Test TL",
};

describe("AuditLoggerService", () => {
  let ds: DataSource;
  let auditLogRepo: AuditLogRepository;
  let auditLogger: AuditLoggerService;

  beforeAll(async () => {
    ds = await createAndInitTestDataSource();
    auditLogRepo = new AuditLogRepository(ds);
    auditLogger = new AuditLoggerService(auditLogRepo);
  });

  afterAll(async () => {
    await ds.destroy();
  });

  afterEach(async () => {
    await clearAllTables(ds);
  });

  it("appends an audit log entry with correct fields", async () => {
    await auditLogger.log({
      user: testUser,
      actionType: "approve",
      releaseFlowId: "rf-001",
      taskId: "t-001",
      context: { reason: "all checks passed" },
    });

    const result = await auditLogRepo.findAll({}, { page: 0, size: 10 });
    expect(result.total).toBe(1);
    const entry = result.data[0];
    expect(entry.operatorId).toBe("user-001");
    expect(entry.operatorRole).toBe("TL");
    expect(entry.actionType).toBe("approve");
    expect(entry.releaseFlowId).toBe("rf-001");
    expect(entry.taskId).toBe("t-001");
    expect(entry.contextPayload).toEqual({ reason: "all checks passed" });
  });

  it("logs multiple entries independently", async () => {
    await auditLogger.log({ user: testUser, actionType: "upload" });
    await auditLogger.log({ user: testUser, actionType: "edit", taskId: "t-002" });

    const result = await auditLogRepo.findAll({}, { page: 0, size: 10 });
    expect(result.total).toBe(2);
  });

  it("swallows audit errors without throwing to the caller", async () => {
    // Use a broken repo that always throws.
    const brokenRepo = {
      append: async () => { throw new Error("DB down"); },
      findAll: async () => ({ data: [], total: 0, page: 0, size: 0 }),
    } as unknown as AuditLogRepository;

    const brokenLogger = new AuditLoggerService(brokenRepo);
    // Should not throw.
    await expect(
      brokenLogger.log({ user: testUser, actionType: "upload" })
    ).resolves.toBeUndefined();
  });

  it("logs entry without optional fields (null FKs)", async () => {
    await auditLogger.log({ user: testUser, actionType: "config_update" });

    const result = await auditLogRepo.findAll({}, { page: 0, size: 10 });
    const entry = result.data[0];
    expect(entry.releaseFlowId).toBeNull();
    expect(entry.requestId).toBeNull();
    expect(entry.taskId).toBeNull();
  });

  it("filters audit logs by actionType", async () => {
    await auditLogger.log({ user: testUser, actionType: "upload" });
    await auditLogger.log({ user: testUser, actionType: "approve" });

    const result = await auditLogRepo.findAll(
      { actionType: "upload" },
      { page: 0, size: 10 }
    );
    expect(result.total).toBe(1);
    expect(result.data[0].actionType).toBe("upload");
  });
});
