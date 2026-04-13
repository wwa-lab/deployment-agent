package com.wwa.agenthub.domain.task;

import com.wwa.agenthub.contracts.enums.ActorKind;
import com.wwa.agenthub.contracts.enums.ExecutionStatus;
import com.wwa.agenthub.contracts.enums.ExternalStatus;
import com.wwa.agenthub.util.JsonAttributeConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * TaskExecutionHistory – immutable record of a single execution attempt.
 * New record per rerun (same task id, incremented attemptNumber).
 * Composite unique index on (task_id, attempt_number) enforced at DB level.
 *
 * <p>Oracle note: {@code resultLogs} is declared as CLOB to accommodate
 * large execution log payloads. H2 handles CLOB correctly in tests.
 */
@Entity
@Table(
    name = "DA_TASK_EXECUTION_HISTORY",
    indexes = {
        @Index(name = "IDX_TEH_TASK_ATTEMPT", columnList = "task_id, attempt_number", unique = true),
        @Index(name = "IDX_TEH_TASK", columnList = "task_id")
    }
)
@Getter
@Setter
public class TaskExecutionHistory {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_status", length = 30, nullable = false)
    private ExecutionStatus executionStatus = ExecutionStatus.Running;

    /** Snapshot of task inputParameters at the time of this execution. */
    @Convert(converter = JsonAttributeConverter.class)
    @Column(name = "input_snapshot", columnDefinition = "CLOB")
    private Map<String, Object> inputSnapshot;

    /** JSON summary of the result (set on callback). */
    @Convert(converter = JsonAttributeConverter.class)
    @Column(name = "result_summary", columnDefinition = "CLOB")
    private Map<String, Object> resultSummary;

    /**
     * Full raw execution output logs (CLOB in Oracle, CLOB in H2 for tests).
     * May be large; load only when displaying result viewer.
     */
    @Column(name = "result_logs", columnDefinition = "CLOB")
    private String resultLogs;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    // ─── External execution metadata (AUTO tasks) ────────────────────────────

    /** External system type: "JENKINS" or "ANSIBLE". Null for MANUAL tasks. */
    @Column(name = "external_system_type", length = 30)
    private String externalSystemType;

    /** External system's own execution/build ID. */
    @Column(name = "external_execution_id", length = 255)
    private String externalExecutionId;

    /** URL to view the job in the external system. */
    @Column(name = "external_job_url", length = 2000)
    private String externalJobUrl;

    /** Timestamp when the submission to the external system was made. */
    @Column(name = "submitted_at")
    private Instant submittedAt;

    /** Submission outcome: "SUBMITTED", "FAILED". */
    @Column(name = "submission_status", length = 30)
    private String submissionStatus;

    /** Error message if submission failed. */
    @Column(name = "submission_message", length = 2000)
    private String submissionMessage;

    /** Scope snapshot captured at submit time for configuration resolution stability. */
    @Column(name = "config_application", length = 255)
    private String configApplication;

    @Column(name = "config_snow_group", length = 255)
    private String configSnowGroup;

    @Column(name = "config_agent", length = 255)
    private String configAgent;

    // ─── External status synchronization (polling) ────────────────────────────

    /** Normalized remote state for UI and monitor decisions. */
    @Enumerated(EnumType.STRING)
    @Column(name = "external_status", length = 50)
    private ExternalStatus externalStatus;

    /** Human-readable explanation of the current remote state. */
    @Column(name = "external_status_message", length = 2000)
    private String externalStatusMessage;

    /** Direct click-through to the remote console/log page. */
    @Column(name = "external_log_url", length = 2000)
    private String externalLogUrl;

    /** Direct click-through to the remote approval page (workflow approvals). */
    @Column(name = "external_approval_url", length = 2000)
    private String externalApprovalUrl;

    /** Timestamp of the last successful poll-based state refresh. */
    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    // ─── MVP Foundation Seams (see architecture.md) ──────────────────────────

    /**
     * Who or what triggered this execution attempt. Always {@link ActorKind#HUMAN}
     * in MVP (a human pressed Run / auto-submitted through a controller action).
     * Reserved so that future policy-initiated and AI-initiated executions can
     * be distinguished without retrofitting this immutable history table.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "actor_kind", length = 20, nullable = false)
    private ActorKind actorKind = ActorKind.HUMAN;

    /**
     * Opaque reference describing the actor when it is not the human operator
     * (e.g. {@code policy:<id>}, {@code ai:<model>#<session>}). Null in MVP.
     */
    @Column(name = "actor_ref", length = 255)
    private String actorRef;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }
}
