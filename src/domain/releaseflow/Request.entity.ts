import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  ManyToOne,
  JoinColumn,
  OneToMany,
  PrimaryGeneratedColumn,
  UpdateDateColumn,
  VersionColumn,
} from "typeorm";
import { RequestStatus, Stage } from "../../contracts/enums";
import { ReleaseFlowEntity } from "./ReleaseFlow.entity";
import { TaskEntity } from "../task/Task.entity";

/**
 * Request – a stage-scoped grouping of tasks within a Release Flow.
 * One Request per (releaseFlow, stage) in MVP.
 */
@Entity("DA_REQUEST")
@Index("IDX_REQ_FLOW_STAGE", ["releaseFlowId", "stage"])
export class RequestEntity {
  @PrimaryGeneratedColumn("uuid")
  id!: string;

  @Column({ type: "varchar", name: "release_flow_id" })
  releaseFlowId!: string;

  @ManyToOne(() => ReleaseFlowEntity, (rf) => rf.requests, { onDelete: "CASCADE" })
  @JoinColumn({ name: "release_flow_id" })
  releaseFlow!: ReleaseFlowEntity;

  @Column({ type: "varchar", length: 10 })
  stage!: Stage;

  @Column({ type: "varchar", length: 30, name: "request_status", default: "Pending" })
  requestStatus!: RequestStatus;

  @OneToMany(() => TaskEntity, (t) => t.request, { cascade: ["insert", "update"] })
  tasks!: TaskEntity[];

  @CreateDateColumn({ name: "created_at" })
  createdAt!: Date;

  @UpdateDateColumn({ name: "updated_at" })
  updatedAt!: Date;

  @VersionColumn({ name: "version" })
  version!: number;
}
