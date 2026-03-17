import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  OneToMany,
  PrimaryGeneratedColumn,
  UpdateDateColumn,
  VersionColumn,
} from "typeorm";
import { FlowStatus, ReviewStatus, Stage } from "../../contracts/enums";
import { RequestEntity } from "./Request.entity";

/**
 * Release Flow – top-level grouping of deployment requests across SIT/UAT/PROD.
 *
 * Grouping key: (project_id, normalizedReleaseId) per locked design decision.
 * project_id   ← Excel "Project ID" (primary grouping key for lookup)
 * project_name ← Excel "Project Name" (display label only)
 *
 * Optimistic locking via @VersionColumn to prevent concurrent mutation conflicts.
 */
@Entity("DA_RELEASE_FLOW")
@Index("IDX_RF_PROJECT_RELEASE", ["projectId", "normalizedReleaseId"], { unique: true })
export class ReleaseFlowEntity {
  @PrimaryGeneratedColumn("uuid")
  id!: string;

  /** Primary grouping key – from Excel "Project ID". */
  @Column({ type: "varchar", length: 255, name: "project_id" })
  projectId!: string;

  /** Display label – from Excel "Project Name". */
  @Column({ type: "varchar", length: 255, name: "project_name" })
  projectName!: string;

  /** System-generated Release ID. Format: {stage}-{normalized_project_name}-{seq}. */
  @Column({ type: "varchar", length: 255, nullable: true, name: "release_id" })
  releaseId!: string | null;

  /**
   * Normalised Release ID used as part of the grouping key.
   * Derived from the system-generated release_id (trimmed/lowercased).
   */
  @Column({ type: "varchar", length: 255, name: "normalized_release_id" })
  normalizedReleaseId!: string;

  @Column({ type: "varchar", length: 10, name: "current_stage" })
  currentStage!: Stage;

  @Column({ type: "varchar", length: 30, name: "flow_status", default: "Pending" })
  flowStatus!: FlowStatus;

  @Column({ type: "varchar", length: 30, name: "review_status", default: "Pending_Review" })
  reviewStatus!: ReviewStatus;

  @Column({ type: "varchar", length: 255, nullable: true, name: "review_owner" })
  reviewOwner!: string | null;

  @OneToMany(() => RequestEntity, (r) => r.releaseFlow, { cascade: ["insert", "update"] })
  requests!: RequestEntity[];

  @CreateDateColumn({ name: "created_at" })
  createdAt!: Date;

  @UpdateDateColumn({ name: "updated_at" })
  updatedAt!: Date;

  /** Optimistic locking counter – incremented on every successful update. */
  @VersionColumn({ name: "version" })
  version!: number;
}
