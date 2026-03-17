import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
} from "typeorm";
import { AuditActionType } from "../../contracts/enums";

/**
 * AuditLogEntry – immutable, append-only audit record.
 * No updates or deletes are ever issued on this entity.
 * Soft references (nullable FKs) to Release Flow / Request / Task – not enforced at
 * DB level so that audit logs survive entity deletion if that ever occurs.
 */
@Entity("DA_AUDIT_LOG_ENTRY")
@Index("IDX_ALE_TIMESTAMP", ["timestamp"])
@Index("IDX_ALE_OPERATOR", ["operatorId"])
@Index("IDX_ALE_ACTION_TYPE", ["actionType"])
@Index("IDX_ALE_RELEASE_FLOW", ["releaseFlowId"])
export class AuditLogEntryEntity {
  @PrimaryGeneratedColumn("uuid")
  id!: string;

  @Column({ type: "varchar", length: 255, name: "operator_id" })
  operatorId!: string;

  @Column({ type: "varchar", length: 50, name: "operator_role" })
  operatorRole!: string;

  @Column({ type: "varchar", length: 50, name: "action_type" })
  actionType!: AuditActionType;

  @CreateDateColumn({ name: "timestamp" })
  timestamp!: Date;

  /** Soft reference to Release Flow (nullable). */
  @Column({ type: "varchar", length: 36, name: "release_flow_id", nullable: true })
  releaseFlowId!: string | null;

  /** Soft reference to Request (nullable). */
  @Column({ type: "varchar", length: 36, name: "request_id", nullable: true })
  requestId!: string | null;

  /** Soft reference to Task (nullable). */
  @Column({ type: "varchar", length: 36, name: "task_id", nullable: true })
  taskId!: string | null;

  /**
   * Arbitrary JSON context for the action.
   * e.g. { fileName, importedCount } for upload; { field, oldValue, newValue } for edit.
   */
  @Column({ type: "text", name: "context_payload", nullable: true })
  contextPayloadJson!: string | null;

  get contextPayload(): Record<string, unknown> | null {
    return this.contextPayloadJson ? JSON.parse(this.contextPayloadJson) : null;
  }
}
