import {
  Column,
  Entity,
  Index,
  JoinColumn,
  ManyToOne,
  OneToMany,
  PrimaryGeneratedColumn,
  UpdateDateColumn,
  VersionColumn,
} from "typeorm";
import { ExecutionType, TaskStatus } from "../../contracts/enums";
import { RequestEntity } from "../releaseflow/Request.entity";
import { TaskExecutionHistoryEntity } from "./TaskExecutionHistory.entity";

/**
 * Task – atomic unit of work within a Request.
 * One row in the Excel AMH_HCC_task sheet = one Task.
 *
 * Same task_id is reused across reruns; each attempt creates a new
 * TaskExecutionHistory record (locked design decision).
 * Editable only in [Pending, Ready_For_Execution] states.
 *
 * Field sources:
 *   task_group_id     ← Excel "Task ID"
 *   task_group_name   ← Excel "Task Name"
 *   step_seq          ← Excel "Step seq#"
 *   task_name         ← Excel "Step"
 *   execution_type    ← Excel "Execution Type" (MANUAL | AUTO)
 *   input_parameters  ← Excel "Script to be executed" + "Parameter (input)"
 *   expected_output   ← Excel "Parameter (Expected Output)"
 *   owner             ← Excel "Owner"
 *   planned_start_time← Excel "Planned Start date/time"
 *   planned_end_time  ← Excel "Planned End date/time"
 *   import_metadata   ← JSON blob: activity_category, common, dependencies, validation
 */
@Entity("DA_TASK")
@Index("IDX_TASK_REQUEST", ["requestId"])
@Index("IDX_TASK_STATUS", ["taskStatus"])
@Index("IDX_TASK_GROUP_SEQ", ["taskGroupId", "stepSeq"])
@Index("IDX_TASK_EXECUTION_TYPE", ["executionType"])
export class TaskEntity {
  @PrimaryGeneratedColumn("uuid")
  id!: string;

  @Column({ type: "varchar", name: "request_id" })
  requestId!: string;

  @ManyToOne(() => RequestEntity, (r) => r.tasks, { onDelete: "CASCADE" })
  @JoinColumn({ name: "request_id" })
  request!: RequestEntity;

  // ─── Core workflow fields (from template) ───────────────────────────────

  /** From Excel "Task ID". Groups related steps for display/ordering. */
  @Column({ type: "varchar", length: 255, name: "task_group_id" })
  taskGroupId!: string;

  /** From Excel "Task Name". Display label for the task group. */
  @Column({ type: "varchar", length: 255, name: "task_group_name" })
  taskGroupName!: string;

  /** From Excel "Step seq#". Execution ordering within task_group_id. */
  @Column({ type: "integer", name: "step_seq" })
  stepSeq!: number;

  /** From Excel "Step". Name of this atomic execution step. */
  @Column({ type: "varchar", length: 255, name: "task_name" })
  taskName!: string;

  /**
   * From Excel "Execution Type": MANUAL | AUTO.
   * MANUAL = human-executed externally; TL records result.
   * AUTO   = system-submitted to execution pipeline; result received via callback.
   */
  @Column({ type: "varchar", length: 10, name: "execution_type" })
  executionType!: ExecutionType;

  @Column({ type: "varchar", length: 30, name: "task_status", default: "Pending" })
  taskStatus!: TaskStatus;

  /**
   * Task input parameters as JSON: { "script": "...", "parameters": "..." }.
   * Editable only when taskStatus is Pending or Ready_For_Execution.
   */
  @Column({ type: "text", name: "input_parameters", nullable: true })
  inputParametersJson!: string | null;

  /** From Excel "Parameter (Expected Output)". Shown during result review. */
  @Column({ type: "text", name: "expected_output", nullable: true })
  expectedOutput!: string | null;

  // ─── Display-only fields (no workflow role) ──────────────────────────────

  /** From Excel "Owner". Display only. */
  @Column({ type: "varchar", length: 255, name: "owner", nullable: true })
  owner!: string | null;

  /** From Excel "Planned Start date/time". Display only; does not gate execution. */
  @Column({ type: "datetime", name: "planned_start_time", nullable: true })
  plannedStartTime!: Date | null;

  /** From Excel "Planned End date/time". Display only; does not gate execution. */
  @Column({ type: "datetime", name: "planned_end_time", nullable: true })
  plannedEndTime!: Date | null;

  // ─── Raw import metadata (no business logic in MVP) ──────────────────────

  /**
   * JSON blob preserving raw import columns with no workflow role:
   *   activity_category, common, dependencies, validation.
   * No business logic reads this in MVP; preserved for reference only.
   */
  @Column({ type: "text", name: "import_metadata", nullable: true })
  importMetadataJson!: string | null;

  // ─── Execution tracking ──────────────────────────────────────────────────

  /** Summary of the latest execution result – JSON string (nullable until first execution). */
  @Column({ type: "text", name: "current_result_summary", nullable: true })
  currentResultSummaryJson!: string | null;

  /** References the execution_id of the most recent TaskExecutionHistory record. */
  @Column({ type: "varchar", length: 36, name: "latest_execution_id", nullable: true })
  latestExecutionId!: string | null;

  /** Actual start time populated by the Execution Service (NOT from template). */
  @Column({ type: "datetime", name: "start_time", nullable: true })
  startTime!: Date | null;

  /** Actual end time populated from execution callback (NOT from template). */
  @Column({ type: "datetime", name: "end_time", nullable: true })
  endTime!: Date | null;

  @UpdateDateColumn({ name: "last_updated_at" })
  lastUpdatedAt!: Date;

  @VersionColumn({ name: "version" })
  version!: number;

  @OneToMany(() => TaskExecutionHistoryEntity, (h) => h.task)
  executionHistory!: TaskExecutionHistoryEntity[];

  // ─── Computed accessors ───────────────────────────────────────────────────

  get inputParameters(): Record<string, unknown> | null {
    return this.inputParametersJson ? JSON.parse(this.inputParametersJson) : null;
  }

  get currentResultSummary(): Record<string, unknown> | null {
    return this.currentResultSummaryJson
      ? JSON.parse(this.currentResultSummaryJson)
      : null;
  }

  get importMetadata(): Record<string, unknown> | null {
    return this.importMetadataJson ? JSON.parse(this.importMetadataJson) : null;
  }
}
