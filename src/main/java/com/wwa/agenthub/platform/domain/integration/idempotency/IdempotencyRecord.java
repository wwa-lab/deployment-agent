package com.wwa.agenthub.platform.domain.integration.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
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
        name = "DA_INTEGRATION_IDEMPOTENCY",
        indexes = {
                @Index(
                        name = "UK_II_COMMAND",
                        columnList = "principal_id, http_method, canonical_path, idempotency_key_hash",
                        unique = true),
                @Index(name = "IDX_II_EXPIRES", columnList = "expires_at")
        }
)
@Getter
@Setter
public class IdempotencyRecord {

    public enum State {
        IN_PROGRESS,
        COMPLETED
    }

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "principal_id", length = 255, nullable = false)
    private String principalId;

    @Column(name = "client_application_id", length = 255, nullable = false)
    private String clientApplicationId;

    @Column(name = "http_method", length = 10, nullable = false)
    private String httpMethod;

    @Column(name = "canonical_path", length = 1000, nullable = false)
    private String canonicalPath;

    @Column(name = "idempotency_key_hash", length = 64, nullable = false)
    private String idempotencyKeyHash;

    @Column(name = "request_fingerprint", length = 64, nullable = false)
    private String requestFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_state", length = 20, nullable = false)
    private State state;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Lob
    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "resource_location", length = 1000)
    private String resourceLocation;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

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
