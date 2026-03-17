import "reflect-metadata";
import { initializeDataSource, createOracleDataSource } from "./db/dataSource";
import { ConfigurationRepository } from "./domain/configuration/ConfigurationRepository";
import { AuditLogRepository } from "./domain/audit/AuditLogRepository";
import { AuditLoggerService } from "./domain/audit/AuditLoggerService";
import { ConfigurationService } from "./domain/configuration/ConfigurationService";
import { ReleaseFlowRepository } from "./domain/releaseflow/ReleaseFlowRepository";
import { RequestRepository } from "./domain/releaseflow/RequestRepository";
import { RequestService } from "./domain/releaseflow/RequestService";
import { ReleaseFlowService } from "./domain/releaseflow/ReleaseFlowService";
import { TaskRepository } from "./domain/task/TaskRepository";
import { TaskService } from "./domain/task/TaskService";
import { TaskExecutionHistoryRepository } from "./domain/task/TaskExecutionHistoryRepository";
import { TaskExecutionHistoryService } from "./domain/task/TaskExecutionHistoryService";
import { DecisionEngine } from "./domain/decision/DecisionEngine";
import { ReleaseFlowProgressionService } from "./domain/decision/ReleaseFlowProgressionService";
import { buildServer } from "./http/server";

async function main() {
  const ds = await initializeDataSource(createOracleDataSource());

  // Audit and Configuration
  const auditLogRepo = new AuditLogRepository(ds);
  const configRepo = new ConfigurationRepository(ds);
  const auditLogger = new AuditLoggerService(auditLogRepo);
  const configService = new ConfigurationService(configRepo, auditLogger);

  // Release Flow and Request
  const releaseFlowRepo = new ReleaseFlowRepository(ds);
  const requestRepo = new RequestRepository(ds);
  const requestService = new RequestService(ds, requestRepo);
  const releaseFlowService = new ReleaseFlowService(ds, releaseFlowRepo, requestRepo);

  // Task and Execution
  const taskRepo = new TaskRepository(ds);
  const taskService = new TaskService(taskRepo, auditLogger);
  const executionHistoryRepo = new TaskExecutionHistoryRepository(ds);
  const executionHistoryService = new TaskExecutionHistoryService(executionHistoryRepo, taskRepo);

  // Decision Engine
  const decisionEngine = new DecisionEngine(taskService, executionHistoryService, auditLogger, ds);
  const progressionService = new ReleaseFlowProgressionService(
    taskRepo,
    requestRepo,
    requestService,
    releaseFlowService,
    releaseFlowRepo
  );

  const app = await buildServer({
    configurationService: configService,
    decisionEngine,
    releaseFlowProgressionService: progressionService,
    releaseFlowService,
    releaseFlowRepo,
    requestRepo,
    requestService,
    taskService,
    taskRepo,
    executionHistoryService,
    executionHistoryRepo,
    auditLogger,
    auditLogRepo,
    ds,
  });

  const host = process.env.HOST ?? "0.0.0.0";
  const port = Number(process.env.PORT ?? 3000);
  await app.listen({ host, port });
  console.log(`Deployment Agent listening on ${host}:${port}`);
}

main().catch((err) => {
  console.error("Failed to start:", err);
  process.exit(1);
});
