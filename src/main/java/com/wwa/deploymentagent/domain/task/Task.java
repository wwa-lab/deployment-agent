package com.wwa.deploymentagent.domain.task;

import com.wwa.deploymentagent.contracts.enums.ExecutionType;
import com.wwa.deploymentagent.contracts.enums.TaskStatus;
import com.wwa.deploymentagent.domain.releaseflow.Request;
import com.wwa.deploymentagent.util.JsonAttributeConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Task – atomic unit of work within a Request.
 * One row in the Excel AMH_HCC_task sheet = one Task.
 *
 * <p>Same task id is reused across reruns; each execution attempt creates a new
 * {@link TaskExecutionHistory} record (locked design decision).
 * Editable only in [Pending, Ready_For_Execution] states.
 *
 * <p>Field sources:
 * <ul>
 *   <li>{@code taskGroupId}      ← Excel "Task ID"</li>
 *   <li>{@code taskGroupName}    ← Excel "Task Name"</li>
 *   <li>{@code stepSeq}         ← Excel "Step seq#"</li>
 *   <li>{@code taskName}        ← Excel "Step"</li>
 *   <li>{@code executionType}   ← Excel "Execution Type" (MANUAL | AUTO)</li>
 *   <li>{@code inputParameters} ← Excel "Script to be executed" + "Parameter (input)"</li>
 *   <li>{@code expectedOutput}  ← Excel "Parameter (Expected Output)"</li>
 *   <li>{@code owner}           ← Excel "Owner"</li>
 *   <li>{@code plannedStartTime}← Excel "Planned Start date/time"</li>
 *   <li>{@code plannedEndTime}  ← Excel "Planned End date/time"</li>
 *   <li>{@code importMetadata}  ← JSON blob: activity_category, common, dependencies, validation</li>
 * </ul>
 */
@Entity
@Table(
    name = "DA_TASK",
    indexes = {
        @Index(name = "IDX_TASK_REQUEST", columnList = "request_id"),
        @Index(name = "IDX_TASK_STATUS", columnList = "task_status"),
        @Index(name = "IDX_TASK_GROUP_SEQ", columnList = "task_group_id, step_seq"),
        @Index(name = "IDX_TASK_EXECUTION_TYPE", columnList = "execution_type")
    }
)
@Getter
@Setter
public class Task {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private Request request;

    // ─── Core workflow fields (from template) ────────────────────────────────

    /** From Excel "Task ID". Groups related steps for display/ordering. */
    @Column(name = "task_group_id", length = 255, nullable = false)
    private String taskGroupId;

    /** From Excel "Task Name". Display label for the task group. */
    @Column(name = "task_group_name", length = 255, nullable = false)
    private String taskGroupName;

    /** From Excel "Step seq#". Execution ordering within task_group_id. */
    @Column(name = "step_seq", nullable = false)
    private Integer stepSeq;

    /** From Excel "Step". Name of this atomic execution step. */
    @Column(name = "task_name", length = 255, nullable = false)
    private String taskName;

    /**
     * From Excel "Execution Type": MANUAL | AUTO.
     * MANUAL = human-executed externally; TL records result.
     * AUTO   = system-submitted to execution pipeline; result received via callback.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "execution_type", length = 10, nullable = false)
    private ExecutionType executionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_status", length = 30, nullable = false)
    private TaskStatus taskStatus = TaskStatus.Pending;

    /**
     * Task input parameters as JSON: { "script": "...", "parameters": "..." }.
     * Editable only when taskStatus is Pending or Ready_For_Execution.
     *
     * Oracle note: stored as CLOB for large payloads. H2 handles CLOB correctly in tests.
     */
    @Convert(converter = JsonAttributeConverter.class)
    @Column(name = "input_parameters", columnDefinition = "CLOB")
    private Map<String, Object> inputParameters;

    /** From Excel "Parameter (Expected Output)". Shown during result review. */
    @Column(name = "expected_output", columnDefinition = "CLOB")
    private String expectedOutput;

    // ─── Display-only fields (no workflow role) ──────────────────────────────

    /** From Excel "Owner". Display only. */
    @Column(name = "owner", length = 255)
    private String owner;

    /** From Excel "Planned Start date/time". Display only; does not gate execution. */
    @Column(name = "planned_start_time")
    private Instant plannedStartTime;

    /** From Excel "Planned End date/time". Display only; does not gate execution. */
    @Column(name = "planned_end_time")
    private Instant plannedEndTime;

    // ─── Raw import metadata (no business logic in MVP) ──────────────────────

    /**
     * JSON blob preserving raw import columns with no workflow role:
     * activity_category, common, dependencies, validation.
     * No business logic reads this in MVP; preserved for reference only.
     */
    @Convert(converter = JsonAttributeConverter.class)
    @Column(name = "import_metadata", columnDefinition = "CLOB")
    private Map<String, Object> importMetadata;

    // ─── Execution tracking ──────────────────────────────────────────────────

    /** Summary of the latest execution result – JSON map (null until first execution). */
    @Convert(converter = JsonAttributeConverter.class)
    @Column(name = "current_result_summary", columnDefinition = "CLOB")
    private Map<String, Object> currentResultSummary;

    /** References the id of the most recent TaskExecutionHistory record. */
    @Column(name = "latest_execution_id", length = 36)
    private String latestExecutionId;

    /** Actual start time populated by the Execution Service (NOT from template). */
    @Column(name = "start_time")
    private Instant startTime;

    /** Actual end time populated from execution callback (NOT from template). */
    @Column(name = "end_time")
    private Instant endTime;

    @UpdateTimestamp
    @Column(name = "last_updated_at", nullable = false)
    private Instant lastUpdatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("attemptNumber ASC")
    private List<TaskExecutionHistory> executionHistory = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }
}
