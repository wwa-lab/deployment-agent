import { describe, expect, it } from "vitest";
import { isValidTaskTransition } from "../../../src/domain/task/taskStateMachine";

describe("taskStateMachine", () => {
  describe("isValidTaskTransition", () => {
    it("allows Pending → Ready_For_Execution", () => {
      expect(isValidTaskTransition("Pending", "Ready_For_Execution")).toBe(true);
    });

    it("allows Pending → Skipped", () => {
      expect(isValidTaskTransition("Pending", "Skipped")).toBe(true);
    });

    it("disallows Pending → Executing", () => {
      expect(isValidTaskTransition("Pending", "Executing")).toBe(false);
    });

    it("allows Ready_For_Execution → Executing", () => {
      expect(isValidTaskTransition("Ready_For_Execution", "Executing")).toBe(true);
    });

    it("allows Ready_For_Execution → Skipped", () => {
      expect(isValidTaskTransition("Ready_For_Execution", "Skipped")).toBe(true);
    });

    it("disallows Ready_For_Execution → Approved", () => {
      expect(isValidTaskTransition("Ready_For_Execution", "Approved")).toBe(false);
    });

    it("allows Executing → Awaiting_Review", () => {
      expect(isValidTaskTransition("Executing", "Awaiting_Review")).toBe(true);
    });

    it("allows Executing → Failed", () => {
      expect(isValidTaskTransition("Executing", "Failed")).toBe(true);
    });

    it("disallows Executing → Approved", () => {
      expect(isValidTaskTransition("Executing", "Approved")).toBe(false);
    });

    it("allows Awaiting_Review → Approved", () => {
      expect(isValidTaskTransition("Awaiting_Review", "Approved")).toBe(true);
    });

    it("allows Awaiting_Review → Rejected", () => {
      expect(isValidTaskTransition("Awaiting_Review", "Rejected")).toBe(true);
    });

    it("disallows Awaiting_Review → Executing", () => {
      expect(isValidTaskTransition("Awaiting_Review", "Executing")).toBe(false);
    });

    it("allows Approved → (no transitions, terminal state)", () => {
      expect(isValidTaskTransition("Approved", "Pending")).toBe(false);
      expect(isValidTaskTransition("Approved", "Ready_For_Execution")).toBe(false);
    });

    it("allows Rejected → Ready_For_Execution (rerun)", () => {
      expect(isValidTaskTransition("Rejected", "Ready_For_Execution")).toBe(true);
    });

    it("disallows Rejected → Approved", () => {
      expect(isValidTaskTransition("Rejected", "Approved")).toBe(false);
    });

    it("allows Skipped → (no transitions, terminal state)", () => {
      expect(isValidTaskTransition("Skipped", "Pending")).toBe(false);
      expect(isValidTaskTransition("Skipped", "Ready_For_Execution")).toBe(false);
    });

    it("allows Failed → Ready_For_Execution (rerun)", () => {
      expect(isValidTaskTransition("Failed", "Ready_For_Execution")).toBe(true);
    });

    it("disallows Failed → Approved", () => {
      expect(isValidTaskTransition("Failed", "Approved")).toBe(false);
    });
  });
});
