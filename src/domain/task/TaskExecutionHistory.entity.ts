import {
  Column,
  Entity,
  Index,
  JoinColumn,
  ManyToOne,
  PrimaryGeneratedColumn,
} from "typeorm";
import { ExecutionStatus } from "../../contracts/enums";
import { TaskEntity } from "./Task.entity";

/**
 * TaskExecutionHistory – immutable record of a single execution attempt.
 * New record per rerun (same task_id, incremented attempt_number).
 * Composite unique index on (task_id, attempt_number) enforced at DB level.
 */
@Entity("DA_TASK_EXECUTION_HISTORY")
@Index("IDX_TEH_TASK_ATTEMPT", ["taskId", "attemptNumber"], { unique: true })
@Index("IDX_TEH_TASK", ["taskId"])
export class TaskExecutionHistoryEntity {
  @PrimaryGeneratedColumn("uuid")
  id!: string;

  @Column({ type: "varchar", name: "task_id" })
  taskId!: string;

  @ManyToOne(() => TaskEntity, (t) => t.executionHistory, { onDelete: "CASCADE" })
  @JoinColumn({ name: "task_id" })
  task!: TaskEntity;

  @Column({ type: "integer", name: "attempt_number" })
  attemptNumber!: number;

  @Column({ type: "varchar", length: 30, name: "execution_status", default: "Running" })
  executionStatus!: ExecutionStatus;

  /** Snapshot of task input_parameters at the time of this execution. */
  @Column({ type: "text", name: "input_snapshot", nullable: true })
  inputSnapshotJson!: string | null;

  /** JSON summary of the result (set on callback). */
  @Column({ type: "text", name: "result_summary", nullable: true })
  resultSummaryJson!: string | null;

  /**
   * Full raw execution output logs (CLOB in Oracle, TEXT in SQLite).
   * May be large; load lazily when displaying result viewer.
   */
  @Column({ type: "text", name: "result_logs", nullable: true })
  resultLogs!: string | null;

  @Column({ type: "datetime", name: "start_time" })
  startTime!: Date;

  @Column({ type: "datetime", name: "end_time", nullable: true })
  endTime!: Date | null;

  get inputSnapshot(): Record<string, unknown> | null {
    return this.inputSnapshotJson ? JSON.parse(this.inputSnapshotJson) : null;
  }

  get resultSummary(): Record<string, unknown> | null {
    return this.resultSummaryJson ? JSON.parse(this.resultSummaryJson) : null;
  }
}
