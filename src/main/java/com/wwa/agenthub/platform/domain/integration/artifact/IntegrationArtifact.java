package com.wwa.agenthub.platform.domain.integration.artifact;

import com.wwa.agenthub.contracts.enums.ArtifactRole;
import com.wwa.agenthub.contracts.enums.ArtifactStorageMode;
import com.wwa.agenthub.domain.task.Task;
import com.wwa.agenthub.domain.task.TaskExecutionHistory;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "DA_INTEGRATION_ARTIFACT",
        indexes = {
                @Index(name = "IDX_IA_EXECUTION", columnList = "execution_id, created_at"),
                @Index(name = "IDX_IA_TASK", columnList = "task_id"),
                @Index(name = "IDX_IA_DIGEST", columnList = "sha256")
        }
)
@Getter
@Setter
public class IntegrationArtifact {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execution_id", nullable = false)
    private TaskExecutionHistory execution;

    @Enumerated(EnumType.STRING)
    @Column(name = "artifact_role", length = 20, nullable = false)
    private ArtifactRole role;

    @Column(name = "artifact_kind", length = 128, nullable = false)
    private String kind;

    @Column(name = "artifact_name", length = 255, nullable = false)
    private String name;

    @Column(name = "media_type", length = 255, nullable = false)
    private String mediaType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "sha256", length = 64, nullable = false)
    private String sha256;

    @Column(name = "source_path", length = 1024)
    private String sourcePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_mode", length = 20, nullable = false)
    private ArtifactStorageMode storageMode;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "content_blob")
    private byte[] content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reference_artifact_id")
    private IntegrationArtifact referenceArtifact;

    @Column(name = "created_by", length = 255, nullable = false)
    private String createdBy;

    @Column(name = "client_application_id", length = 255, nullable = false)
    private String clientApplicationId;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "content_expires_at")
    private Instant contentExpiresAt;

    @Column(name = "content_purged_at")
    private Instant contentPurgedAt;

    @Column(name = "legal_hold", nullable = false)
    private boolean legalHold;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }
}
