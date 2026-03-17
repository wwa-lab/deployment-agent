import { describe, expect, it } from "vitest";
import { validateTaskInput } from "../../../src/domain/task/taskInputValidation";

describe("taskInputValidation", () => {
  describe("validateTaskInput", () => {
    it("accepts valid objects", () => {
      const result = validateTaskInput("deploy", { version: "1.0.0", env: "staging" });
      expect(result).toBeNull();
    });

    it("accepts valid strings", () => {
      const result = validateTaskInput("smoke_test", "https://example.com");
      expect(result).toBeNull();
    });

    it("accepts valid numbers", () => {
      const result = validateTaskInput("wait", 5000);
      expect(result).toBeNull();
    });

    it("accepts arrays", () => {
      const result = validateTaskInput("batch", [1, 2, 3]);
      expect(result).toBeNull();
    });

    it("accepts null", () => {
      const result = validateTaskInput("no-op", null);
      expect(result).toBeNull();
    });

    it("rejects undefined", () => {
      const result = validateTaskInput("deploy", undefined);
      expect(result).not.toBeNull();
      expect(result).toContain("undefined");
    });

    it("returns null for any valid taskType", () => {
      const taskTypes = ["deploy", "smoke_test", "database_migration", "custom_task"];
      for (const taskType of taskTypes) {
        const result = validateTaskInput(taskType, { test: "data" });
        expect(result).toBeNull();
      }
    });
  });
});
