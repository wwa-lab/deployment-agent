import "reflect-metadata";
import { DataSource, DataSourceOptions } from "typeorm";
import { ReleaseFlowEntity } from "../domain/releaseflow/ReleaseFlow.entity";
import { RequestEntity } from "../domain/releaseflow/Request.entity";
import { TaskEntity } from "../domain/task/Task.entity";
import { TaskExecutionHistoryEntity } from "../domain/task/TaskExecutionHistory.entity";
import { ConfigurationItemEntity } from "../domain/configuration/ConfigurationItem.entity";
import { AuditLogEntryEntity } from "../domain/audit/AuditLogEntry.entity";

const ENTITIES = [
  ReleaseFlowEntity,
  RequestEntity,
  TaskEntity,
  TaskExecutionHistoryEntity,
  ConfigurationItemEntity,
  AuditLogEntryEntity,
];

/**
 * Oracle DataSource for production.
 * Requires environment variables:
 *   DB_HOST, DB_PORT, DB_SERVICE, DB_USER, DB_PASSWORD
 */
export function createOracleDataSource(): DataSource {
  const options: DataSourceOptions = {
    type: "oracle",
    host: process.env.DB_HOST ?? "localhost",
    port: Number(process.env.DB_PORT ?? 1521),
    serviceName: process.env.DB_SERVICE ?? "ORCL",
    username: process.env.DB_USER,
    password: process.env.DB_PASSWORD,
    entities: ENTITIES,
    synchronize: false,
    logging: process.env.DB_LOGGING === "true" ? "all" : ["error"],
  };
  return new DataSource(options);
}

/**
 * sql.js (pure WASM SQLite) DataSource for integration tests.
 * Uses TypeORM's built-in sqljs driver — no native compilation required.
 * Each call produces an independent in-memory database (no state shared between suites).
 */
export async function createTestDataSource(): Promise<DataSource> {
  // sql.js is a pure-JS/WASM SQLite port; load it at runtime so the import
  // does not affect the production bundle which has no sql.js dependency.
  const SqlJs = (await import("sql.js")).default;
  const driver = await SqlJs();
  const options: DataSourceOptions = {
    type: "sqljs",
    driver,
    entities: ENTITIES,
    synchronize: true, // auto-create schema from entities in tests
    logging: false,
  };
  return new DataSource(options);
}

/** Singleton production DataSource (initialized in main.ts). */
let appDataSource: DataSource | null = null;

export function getAppDataSource(): DataSource {
  if (!appDataSource) {
    throw new Error(
      "DataSource not initialized. Call initializeDataSource() first."
    );
  }
  return appDataSource;
}

export async function initializeDataSource(
  ds: DataSource = createOracleDataSource()
): Promise<DataSource> {
  appDataSource = ds;
  if (!appDataSource.isInitialized) {
    await appDataSource.initialize();
  }
  return appDataSource;
}
