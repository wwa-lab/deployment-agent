import Fastify, { FastifyInstance } from "fastify";
import { AppError } from "../errors/AppError";
import { ConfigurationService } from "../domain/configuration/ConfigurationService";
import { DecisionEngine } from "../domain/decision/DecisionEngine";
import { ReleaseFlowProgressionService } from "../domain/decision/ReleaseFlowProgressionService";
import { ReleaseFlowService } from "../domain/releaseflow/ReleaseFlowService";
import { ReleaseFlowRepository } from "../domain/releaseflow/ReleaseFlowRepository";
import { RequestRepository } from "../domain/releaseflow/RequestRepository";
import { RequestService } from "../domain/releaseflow/RequestService";
import { TaskService } from "../domain/task/TaskService";
import { TaskRepository } from "../domain/task/TaskRepository";
import { TaskExecutionHistoryService } from "../domain/task/TaskExecutionHistoryService";
import { TaskExecutionHistoryRepository } from "../domain/task/TaskExecutionHistoryRepository";
import { AuditLoggerService } from "../domain/audit/AuditLoggerService";
import { AuditLogRepository } from "../domain/audit/AuditLogRepository";
import { registerConfigurationRoutes } from "./handlers/ConfigurationHandler";
import { registerDecisionRoutes } from "./handlers/DecisionHandler";
import { registerAuditLogRoutes } from "./handlers/AuditLogHandler";
import { registerReleaseFlowRoutes } from "./handlers/ReleaseFlowHandler";
import { registerTaskRoutes } from "./handlers/TaskHandler";
import { extractUserContext } from "./middleware/auth";
import { OptimisticLockVersionMismatchError } from "typeorm";
import { OptimisticLockConflictError } from "../errors/AppError";
import { DataSource } from "typeorm";

export interface ServerDeps {
  configurationService: ConfigurationService;
  decisionEngine: DecisionEngine;
  releaseFlowProgressionService: ReleaseFlowProgressionService;
  releaseFlowService: ReleaseFlowService;
  releaseFlowRepo: ReleaseFlowRepository;
  requestRepo: RequestRepository;
  requestService: RequestService;
  taskService: TaskService;
  taskRepo: TaskRepository;
  executionHistoryService: TaskExecutionHistoryService;
  executionHistoryRepo: TaskExecutionHistoryRepository;
  auditLogger: AuditLoggerService;
  auditLogRepo: AuditLogRepository;
  ds: DataSource;
}

/**
 * Builds and returns a configured Fastify instance.
 * Separate from main.ts to support testing with test DataSource.
 */
export async function buildServer(deps: ServerDeps): Promise<FastifyInstance> {
  const app = Fastify({ logger: process.env.NODE_ENV !== "test" });

  // Auth pre-handler registered once at the root level – applies to all routes.
  // Reads X-User-Id / X-User-Role headers injected by the WWA platform.
  app.addHook("preHandler", extractUserContext);

  // Centralised error handler (T10.3) – maps AppError and TypeORM errors to HTTP responses.
  app.setErrorHandler((error, _req, reply) => {
    // Translate TypeORM optimistic lock to our error type.
    if (error instanceof OptimisticLockVersionMismatchError) {
      const appErr = new OptimisticLockConflictError("entity");
      return reply.status(appErr.statusCode).send({
        error: appErr.code,
        message: appErr.message,
      });
    }

    if (error instanceof AppError) {
      return reply.status(error.statusCode).send({
        error: error.code,
        message: error.message,
        ...(error.details !== undefined ? { details: error.details } : {}),
      });
    }

    // Unknown error – do not leak internals.
    app.log.error(error);
    return reply.status(500).send({
      error: "INTERNAL_ERROR",
      message: "An unexpected error occurred.",
    });
  });

  registerConfigurationRoutes(app, deps.configurationService);
  registerDecisionRoutes(app, deps.decisionEngine, deps.releaseFlowProgressionService, deps.taskService);
  registerAuditLogRoutes(app, deps.auditLogRepo);
  registerReleaseFlowRoutes(app, deps.releaseFlowService, deps.releaseFlowRepo, deps.requestRepo, deps.taskRepo);
  registerTaskRoutes(app, deps.taskService, deps.taskRepo, deps.executionHistoryService);

  return app;
}
