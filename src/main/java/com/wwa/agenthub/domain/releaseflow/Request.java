package com.wwa.agenthub.domain.releaseflow;

import com.wwa.agenthub.contracts.enums.RequestStatus;
import com.wwa.agenthub.domain.task.Task;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Request – a stage-scoped grouping of tasks within a Release Flow.
 * Multiple attempts are allowed per (releaseFlow, stage).
 */
@Entity
@Table(
    name = "DA_REQUEST",
    indexes = {
        @Index(name = "IDX_REQ_FLOW_STAGE", columnList = "release_flow_id, stage"),
        @Index(
                name = "UK_REQ_FLOW_STAGE_ATTEMPT",
                columnList = "release_flow_id, stage, attempt_number",
                unique = true)
    }
)
@Getter
@Setter
public class Request {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "release_flow_id", nullable = false)
    private ReleaseFlow releaseFlow;

    @Column(name = "stage", length = 10, nullable = false)
    private String stage;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_status", length = 30, nullable = false)
    private RequestStatus requestStatus = RequestStatus.Pending;

    @Column(name = "snow_group", length = 255)
    private String snowGroup;

    @Column(name = "application", length = 255)
    private String application;

    @Column(name = "agent", length = 255)
    private String agent;

    @Column(name = "owner", length = 255)
    private String owner;

    @Column(name = "site", length = 100)
    private String site;

    @Column(name = "created_by", length = 255, updatable = false)
    private String createdBy;

    @Column(name = "estimated_remaining_minutes")
    private Integer estimatedRemainingMinutes;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "archived_by", length = 255)
    private String archivedBy;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("taskGroupId ASC, stepSeq ASC")
    private List<Task> tasks = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (attemptNumber == null || attemptNumber < 1) {
            attemptNumber = 1;
        }
    }
}
