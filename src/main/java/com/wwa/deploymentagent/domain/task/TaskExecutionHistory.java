package com.wwa.deploymentagent.domain.task;

import com.wwa.deploymentagent.contracts.enums.ExecutionStatus;
import com.wwa.deploymentagent.util.JsonAttributeConverter;
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

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }
}
