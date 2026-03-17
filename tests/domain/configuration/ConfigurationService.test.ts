import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";
import { DataSource } from "typeorm";
import { ConfigurationRepository } from "../../../src/domain/configuration/ConfigurationRepository";
import { AuditLogRepository } from "../../../src/domain/audit/AuditLogRepository";
import { AuditLoggerService } from "../../../src/domain/audit/AuditLoggerService";
import { ConfigurationService } from "../../../src/domain/configuration/ConfigurationService";
import { ValidationError } from "../../../src/errors/AppError";
import { UserContext } from "../../../src/contracts/UserContext";
import {
  createAndInitTestDataSource,
  clearAllTables,
} from "../../helpers/testDataSource";

const adminUser: UserContext = { userId: "admin-001", role: "DEVOPS_ADMIN" };

describe("ConfigurationService", () => {
  let ds: DataSource;
  let configRepo: ConfigurationRepository;
  let auditLogger: AuditLoggerService;
  let configService: ConfigurationService;

  beforeAll(async () => {
    ds = await createAndInitTestDataSource();
    configRepo = new ConfigurationRepository(ds);
    auditLogger = new AuditLoggerService(new AuditLogRepository(ds));
    configService = new ConfigurationService(configRepo, auditLogger);
  });

  afterAll(async () => {
    await ds.destroy();
  });

  afterEach(async () => {
    await clearAllTables(ds);
  });

  describe("listAll", () => {
    it("returns empty list when no config exists", async () => {
      const items = await configService.listAll();
      expect(items).toHaveLength(0);
    });
  });

  describe("upsert", () => {
    it("creates a new jenkins_url config item", async () => {
      const saved = await configService.upsert(
        { key: "jenkins_url", value: "https://jenkins.example.com" },
        adminUser
      );
      expect(saved.configKey).toBe("jenkins_url");
      expect(saved.configValue).toBe("https://jenkins.example.com");
      expect(saved.updatedBy).toBe("admin-001");
    });

    it("updates an existing config item", async () => {
      await configService.upsert(
        { key: "jenkins_url", value: "https://jenkins.v1.example.com" },
        adminUser
      );
      const updated = await configService.upsert(
        { key: "jenkins_url", value: "https://jenkins.v2.example.com" },
        adminUser
      );
      expect(updated.configValue).toBe("https://jenkins.v2.example.com");

      const all = await configService.listAll();
      expect(all).toHaveLength(1); // No duplicate created.
    });

    it("rejects invalid URL for jenkins_url", async () => {
      await expect(
        configService.upsert({ key: "jenkins_url", value: "not-a-url" }, adminUser)
      ).rejects.toBeInstanceOf(ValidationError);
    });

    it("rejects non-HTTPS for execution_callback_endpoint", async () => {
      await expect(
        configService.upsert(
          { key: "execution_callback_endpoint", value: "http://insecure.example.com" },
          adminUser
        )
      ).rejects.toBeInstanceOf(ValidationError);
    });

    it("accepts https for execution_callback_endpoint", async () => {
      const saved = await configService.upsert(
        {
          key: "execution_callback_endpoint",
          value: "https://callback.example.com/webhook",
        },
        adminUser
      );
      expect(saved.configValue).toBe("https://callback.example.com/webhook");
    });

    it("creates an audit log entry on successful update", async () => {
      await configService.upsert(
        { key: "ansible_url", value: "https://ansible.example.com" },
        adminUser
      );
      const logs = await new AuditLogRepository(ds).findAll(
        { actionType: "config_update" },
        { page: 0, size: 10 }
      );
      expect(logs.total).toBe(1);
      expect(logs.data[0].contextPayload).toMatchObject({
        configKey: "ansible_url",
        newValue: "https://ansible.example.com",
      });
    });

    it("rejects unknown config key", async () => {
      await expect(
        configService.upsert(
          // @ts-expect-error – intentional unknown key test
          { key: "unknown_key", value: "https://example.com" },
          adminUser
        )
      ).rejects.toBeInstanceOf(ValidationError);
    });
  });

  describe("getByKey", () => {
    it("returns null when key does not exist", async () => {
      const result = await configService.getByKey("jenkins_url");
      expect(result).toBeNull();
    });

    it("returns the item after upsert", async () => {
      await configService.upsert(
        { key: "jenkins_url", value: "https://jenkins.example.com" },
        adminUser
      );
      const result = await configService.getByKey("jenkins_url");
      expect(result?.configValue).toBe("https://jenkins.example.com");
    });
  });
});
