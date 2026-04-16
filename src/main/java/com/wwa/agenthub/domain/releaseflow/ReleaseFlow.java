package com.wwa.agenthub.domain.releaseflow;

import com.wwa.agenthub.contracts.enums.FlowStatus;
import com.wwa.agenthub.contracts.enums.ReviewStatus;
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
 * Release Flow – top-level grouping of deployment requests across SIT/UAT/PROD.
 *
 * <p>Grouping key: (project_id, normalized_release_id) — unique index enforced at DB level.
 * <ul>
 *   <li>{@code projectId}           ← Excel "Project ID" (primary lookup key)</li>
 *   <li>{@code projectName}         ← Excel "Project Name" (display label)</li>
 *   <li>{@code releaseId}           ← System-generated: {@code {stage}-{normalized_project_name}-{seq}}</li>
 *   <li>{@code normalizedReleaseId} ← Trimmed/lower-cased release_id; used in uniqueness constraint</li>
 * </ul>
 *
 * <p>Optimistic locking via {@code @Version} prevents concurrent mutation conflicts.
 */
@Entity
@Table(
    name = "DA_RELEASE_FLOW",
    indexes = {
        @Index(name = "IDX_RF_PROJECT_RELEASE", columnList = "project_id, normalized_release_id", unique = true)
    }
)
@Getter
@Setter
public class ReleaseFlow {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    /** Primary grouping key – from Excel "Project ID". */
    @Column(name = "project_id", length = 255, nullable = false)
    private String projectId;

    /** Display label – from Excel "Project Name". */
    @Column(name = "project_name", length = 255, nullable = false)
    private String projectName;

    /** System-generated Release ID. Format: {stage}-{normalized_project_name}-{seq}. */
    @Column(name = "release_id", length = 255)
    private String releaseId;

    /**
     * Normalised Release ID used as part of the unique grouping key.
     * Derived from the system-generated release_id (trimmed and lower-cased).
     */
    @Column(name = "normalized_release_id", length = 255, nullable = false)
    private String normalizedReleaseId;

    @Column(name = "current_stage", length = 64, nullable = false)
    private String currentStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "flow_status", length = 30, nullable = false)
    private FlowStatus flowStatus = FlowStatus.Pending;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", length = 30, nullable = false)
    private ReviewStatus reviewStatus = ReviewStatus.Pending_Review;

    @Column(name = "review_owner", length = 255)
    private String reviewOwner;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "archived_by", length = 255)
    private String archivedBy;

    @OneToMany(mappedBy = "releaseFlow", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Request> requests = new ArrayList<>();

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
    }
}
