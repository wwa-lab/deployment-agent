import { EntityManager } from "typeorm";
import { AuditActionType } from "../../contracts/enums";
import { UserContext } from "../../contracts/UserContext";
import { AuditLogEntryEntity } from "./AuditLogEntry.entity";
import { AuditLogRepository } from "./AuditLogRepository";

export interface AuditEventInput {
  user: UserContext;
  actionType: AuditActionType;
  releaseFlowId?: string;
  requestId?: string;
  taskId?: string;
  context?: Record<string, unknown>;
}

/**
 * AuditLoggerService – centralises audit logging.
 * All audit log writes are append-only.
 * An audit failure logs a warning but does NOT abort the calling business operation.
 */
export class AuditLoggerService {
  constructor(private readonly auditLogRepo: AuditLogRepository) {}

  /**
   * Appends an audit log entry.
   * Accepts an optional EntityManager to participate in the caller's transaction.
   * If the write fails, the error is swallowed with a console.error – audit
   * failures must not corrupt the business flow (per design requirement).
   */
  async log(event: AuditEventInput, em?: EntityManager): Promise<void> {
    try {
      const entry: Omit<AuditLogEntryEntity, "id" | "timestamp"> = {
        operatorId: event.user.userId,
        operatorRole: event.user.role,
        actionType: event.actionType,
        releaseFlowId: event.releaseFlowId ?? null,
        requestId: event.requestId ?? null,
        taskId: event.taskId ?? null,
        contextPayloadJson: event.context
          ? JSON.stringify(event.context)
          : null,
        get contextPayload() {
          return this.contextPayloadJson
            ? JSON.parse(this.contextPayloadJson)
            : null;
        },
      };
      await this.auditLogRepo.append(entry, em);
    } catch (err) {
      // Audit failure must not propagate to caller.
      console.error("[AuditLoggerService] Failed to write audit entry:", err);
    }
  }
}
