package com.wwa.agenthub.platform.domain.integration.review;

import com.wwa.agenthub.contracts.enums.IntegrationReviewDecisionType;
import com.wwa.agenthub.domain.task.Task;
import com.wwa.agenthub.domain.task.TaskExecutionHistory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "DA_INTEGRATION_REVIEW",
        indexes = @Index(name = "UK_IR_EXECUTION", columnList = "execution_id", unique = true)
)
@Getter
@Setter
public class IntegrationReviewDecision {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execution_id", nullable = false)
    private TaskExecutionHistory execution;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", length = 20, nullable = false)
    private IntegrationReviewDecisionType decision;

    @Column(name = "reviewer_id", length = 255, nullable = false)
    private String reviewerId;

    @Column(name = "reviewer_display_name", length = 255, nullable = false)
    private String reviewerDisplayName;

    @Column(name = "review_comment", length = 2000)
    private String comment;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @CreationTimestamp
    @Column(name = "decided_at", nullable = false, updatable = false)
    private Instant decidedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }
}
