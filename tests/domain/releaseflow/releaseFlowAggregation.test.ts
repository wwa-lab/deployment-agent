import { describe, expect, it } from "vitest";
import {
  aggregateTasksToRequestStatus,
  aggregateRequestsToStageStatus,
  aggregateStagesToFlowStatus,
  toSummaryStatus,
} from "../../../src/domain/releaseflow/releaseFlowAggregation";

describe("aggregateTasksToRequestStatus", () => {
  it("returns Pending when task list is empty", () => {
    expect(aggregateTasksToRequestStatus([])).toBe("Pending");
  });

  it("returns Running if any task is Executing", () => {
    expect(
      aggregateTasksToRequestStatus(["Executing", "Pending"])
    ).toBe("Running");
  });

  it("returns Completed when all tasks are Approved or Skipped", () => {
    expect(
      aggregateTasksToRequestStatus(["Approved", "Skipped", "Approved"])
    ).toBe("Completed");
  });

  it("returns Rejected if any task is Rejected", () => {
    expect(
      aggregateTasksToRequestStatus(["Approved", "Rejected"])
    ).toBe("Rejected");
  });

  it("returns Failed if any task is Failed", () => {
    expect(
      aggregateTasksToRequestStatus(["Approved", "Failed"])
    ).toBe("Failed");
  });

  it("returns Pending when tasks are all Pending", () => {
    expect(
      aggregateTasksToRequestStatus(["Pending", "Pending"])
    ).toBe("Pending");
  });

  it("Running takes priority over Rejected for display (Executing present)", () => {
    expect(
      aggregateTasksToRequestStatus(["Executing", "Rejected"])
    ).toBe("Running");
  });
});

describe("aggregateRequestsToStageStatus", () => {
  it("returns Pending when list is empty", () => {
    expect(aggregateRequestsToStageStatus([])).toBe("Pending");
  });

  it("returns Running if any request is Running", () => {
    expect(aggregateRequestsToStageStatus(["Running", "Pending"])).toBe("Running");
  });

  it("returns Completed when all are Completed", () => {
    expect(aggregateRequestsToStageStatus(["Completed", "Completed"])).toBe("Completed");
  });

  it("returns Rejected if any request is Rejected", () => {
    expect(aggregateRequestsToStageStatus(["Completed", "Rejected"])).toBe("Rejected");
  });
});

describe("aggregateStagesToFlowStatus", () => {
  it("returns Pending when empty", () => {
    expect(aggregateStagesToFlowStatus([])).toBe("Pending");
  });

  it("returns Completed when all stages Completed", () => {
    expect(
      aggregateStagesToFlowStatus(["Completed", "Completed", "Completed"])
    ).toBe("Completed");
  });

  it("returns Rejected if any stage Rejected", () => {
    expect(
      aggregateStagesToFlowStatus(["Completed", "Rejected", "Pending"])
    ).toBe("Rejected");
  });

  it("returns Running if any stage Running", () => {
    expect(
      aggregateStagesToFlowStatus(["Completed", "Running", "Pending"])
    ).toBe("Running");
  });
});

describe("toSummaryStatus", () => {
  it("maps Completed to Done", () => {
    expect(toSummaryStatus("Completed")).toBe("Done");
  });

  it("maps Approved to Done", () => {
    expect(toSummaryStatus("Approved")).toBe("Done");
  });

  it("maps Skipped to Done", () => {
    expect(toSummaryStatus("Skipped")).toBe("Done");
  });

  it("maps Executing to Running", () => {
    expect(toSummaryStatus("Executing")).toBe("Running");
  });

  it("maps Awaiting_Review to Running", () => {
    expect(toSummaryStatus("Awaiting_Review")).toBe("Running");
  });

  it("maps Ready_For_Execution to Running", () => {
    expect(toSummaryStatus("Ready_For_Execution")).toBe("Running");
  });

  it("maps Pending to Pending", () => {
    expect(toSummaryStatus("Pending")).toBe("Pending");
  });

  it("maps Rejected to Pending (no separate fail bucket in summary)", () => {
    expect(toSummaryStatus("Rejected")).toBe("Pending");
  });

  it("maps Failed to Pending", () => {
    expect(toSummaryStatus("Failed")).toBe("Pending");
  });
});
