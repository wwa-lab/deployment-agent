import "reflect-metadata";
import { DataSource } from "typeorm";
import { createTestDataSource } from "../../src/db/dataSource";

/**
 * Shared test DataSource lifecycle helpers (T1.5).
 * Each test suite gets its own in-memory SQLite DB (no shared state between suites).
 *
 * Usage:
 *   let ds: DataSource;
 *   beforeAll(async () => { ds = await createAndInitTestDataSource(); });
 *   afterAll(async () => { await ds.destroy(); });
 *   afterEach(async () => { await clearAllTables(ds); });
 */
export async function createAndInitTestDataSource(): Promise<DataSource> {
  const ds = await createTestDataSource();
  await ds.initialize();
  return ds;
}

/**
 * Truncates all entity tables in dependency-safe order.
 * Allows tests to reset state between runs without recreating the DataSource.
 */
export async function clearAllTables(ds: DataSource): Promise<void> {
  // Order matters: children before parents to avoid FK violations.
  await ds.query("DELETE FROM DA_TASK_EXECUTION_HISTORY");
  await ds.query("DELETE FROM DA_TASK");
  await ds.query("DELETE FROM DA_REQUEST");
  await ds.query("DELETE FROM DA_RELEASE_FLOW");
  await ds.query("DELETE FROM DA_AUDIT_LOG_ENTRY");
  await ds.query("DELETE FROM DA_CONFIGURATION_ITEM");
}

/**
 * Seed helpers – reusable across test suites.
 */
import { ReleaseFlowEntity } from "../../src/domain/releaseflow/ReleaseFlow.entity";
import { RequestEntity } from "../../src/domain/releaseflow/Request.entity";
import { TaskEntity } from "../../src/domain/task/Task.entity";

export async function seedReleaseFlow(
  ds: DataSource,
  overrides: Partial<ReleaseFlowEntity> = {}
): Promise<ReleaseFlowEntity> {
  const repo = ds.getRepository(ReleaseFlowEntity);
  const rf = repo.create({
    projectId: "TEST_PROJECT",
    projectName: "Test Project",
    releaseId: "sit-test-project-0001",
    normalizedReleaseId: "sit-test-project-0001",
    currentStage: "SIT",
    flowStatus: "Pending",
    reviewStatus: "Pending_Review",
    reviewOwner: null,
    requests: [],
    ...overrides,
  });
  return repo.save(rf);
}

export async function seedRequest(
  ds: DataSource,
  releaseFlowId: string,
  overrides: Partial<RequestEntity> = {}
): Promise<RequestEntity> {
  const repo = ds.getRepository(RequestEntity);
  const req = repo.create({
    releaseFlowId,
    stage: "SIT",
    requestStatus: "Pending",
    tasks: [],
    ...overrides,
  });
  return repo.save(req);
}

export async function seedTask(
  ds: DataSource,
  requestId: string,
  overrides: Partial<TaskEntity> = {}
): Promise<TaskEntity> {
  const repo = ds.getRepository(TaskEntity);
  const task = repo.create({
    requestId,
    taskGroupId: "TG-001",
    taskGroupName: "Deploy App",
    stepSeq: 1,
    taskName: "deploy-app",
    executionType: "AUTO",
    taskStatus: "Pending",
    inputParametersJson: JSON.stringify({ script: "deploy.sh", parameters: "--env staging" }),
    expectedOutput: null,
    owner: null,
    plannedStartTime: null,
    plannedEndTime: null,
    importMetadataJson: null,
    currentResultSummaryJson: null,
    latestExecutionId: null,
    startTime: null,
    endTime: null,
    ...overrides,
  });
  return repo.save(task);
}

import { TaskExecutionHistoryEntity } from "../../src/domain/task/TaskExecutionHistory.entity";

export async function seedTaskExecutionHistory(
  ds: DataSource,
  taskId: string,
  overrides: Partial<TaskExecutionHistoryEntity> = {}
): Promise<TaskExecutionHistoryEntity> {
  const repo = ds.getRepository(TaskExecutionHistoryEntity);
  const execution = repo.create({
    taskId,
    attemptNumber: 1,
    executionStatus: "Running",
    inputSnapshotJson: JSON.stringify({ script: "deploy.sh", parameters: "--env staging" }),
    resultSummaryJson: null,
    resultLogs: null,
    startTime: new Date(),
    endTime: null,
    ...overrides,
  });
  return repo.save(execution);
}
