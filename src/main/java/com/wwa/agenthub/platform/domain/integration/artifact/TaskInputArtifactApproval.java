package com.wwa.agenthub.platform.domain.integration.artifact;

import com.wwa.agenthub.domain.task.Task;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "DA_TASK_INPUT_ARTIFACT",
        indexes = @Index(
                name = "UK_TIA_TASK_ARTIFACT",
                columnList = "task_id, artifact_id",
                unique = true)
)
@Getter
@Setter
public class TaskInputArtifactApproval {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artifact_id", nullable = false)
    private IntegrationArtifact artifact;

    @Column(name = "approved_by", length = 255, nullable = false)
    private String approvedBy;

    @CreationTimestamp
    @Column(name = "approved_at", nullable = false, updatable = false)
    private Instant approvedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }
}
