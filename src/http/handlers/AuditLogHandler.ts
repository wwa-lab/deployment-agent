import { FastifyInstance } from "fastify";
import { AuditLogRepository, AuditLogFilter } from "../../domain/audit/AuditLogRepository";
import { AuditLogEntryDto, PaginatedResponseDto } from "../../contracts/dtos";
import { AuditActionType } from "../../contracts/enums";
import { requireRole } from "../middleware/auth";
import { ValidationError } from "../../errors/AppError";

/**
 * Audit log handler – REST endpoint for retrieving audit logs.
 *
 * GET /api/deployment-agent/audit-logs
 * - Query params: releaseFlowId, taskId, operatorId, actionType, page (default 0), size (default 10)
 * - Auth: AUDIT, MANAGEMENT, or DEVOPS_ADMIN role required
 * - Response: PaginatedResponseDto<AuditLogEntryDto>
 */
export function registerAuditLogRoutes(
  app: FastifyInstance,
  auditLogRepo: AuditLogRepository
): void {
  app.get<{
    Querystring: {
      releaseFlowId?: string;
      taskId?: string;
      operatorId?: string;
      actionType?: string;
      page?: string;
      size?: string;
    };
  }>("/api/deployment-agent/audit-logs", async (req, reply) => {
    requireRole(req, "audit_log_view", "AUDIT", "MANAGEMENT", "DEVOPS_ADMIN");

    const {
      releaseFlowId,
      taskId,
      operatorId,
      actionType,
      page = "0",
      size = "10",
    } = req.query;

    const pageNum = parseInt(page, 10);
    const sizeNum = parseInt(size, 10);

    if (isNaN(pageNum) || pageNum < 0) {
      throw new ValidationError("Invalid page parameter", { page });
    }

    if (isNaN(sizeNum) || sizeNum < 1) {
      throw new ValidationError("Invalid size parameter", { size });
    }

    if (sizeNum > 100) {
      throw new ValidationError("Page size cannot exceed 100", { size: sizeNum });
    }

    // Build filter
    const filter: AuditLogFilter = {};
    if (releaseFlowId) filter.releaseFlowId = releaseFlowId;
    if (taskId) filter.taskId = taskId;
    if (operatorId) filter.operatorId = operatorId;
    if (actionType) filter.actionType = actionType as unknown as AuditActionType;

    // Fetch audit logs
    const result = await auditLogRepo.findAll(filter, { page: pageNum, size: sizeNum });

    // Map to DTOs
    const dtos: AuditLogEntryDto[] = result.data.map((entry) => ({
      id: entry.id,
      timestamp: entry.timestamp.toISOString(),
      operatorId: entry.operatorId,
      operatorRole: entry.operatorRole,
      actionType: entry.actionType,
      releaseFlowId: entry.releaseFlowId,
      requestId: entry.requestId,
      taskId: entry.taskId,
      contextPayload: entry.contextPayload,
    }));

    const response: PaginatedResponseDto<AuditLogEntryDto> = {
      data: dtos,
      total: result.total,
      page: result.page,
      size: result.size,
    };

    return reply.send(response);
  });
}
