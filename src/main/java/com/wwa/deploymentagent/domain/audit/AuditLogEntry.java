package com.wwa.deploymentagent.domain.audit;

import com.wwa.deploymentagent.contracts.enums.AuditActionType;
import com.wwa.deploymentagent.util.JsonAttributeConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * AuditLogEntry – immutable, append-only audit record.
 *
 * <p>No updates or deletes are ever issued on this entity.
 * Soft references (nullable FKs) to Release Flow / Request / Task are not enforced
 * at DB level so that audit logs survive entity deletion if that ever occurs.
 */
@Entity
@Table(
    name = "DA_AUDIT_LOG_ENTRY",
    indexes = {
        @Index(name = "IDX_ALE_TIMESTAMP", columnList = "timestamp"),
        @Index(name = "IDX_ALE_OPERATOR", columnList = "operator_id"),
        @Index(name = "IDX_ALE_ACTION_TYPE", columnList = "action_type"),
        @Index(name = "IDX_ALE_RELEASE_FLOW", columnList = "release_flow_id"),
        @Index(name = "IDX_ALE_APPLICATION", columnList = "application"),
        @Index(name = "IDX_ALE_SNOW_GROUP", columnList = "snow_group"),
        @Index(name = "IDX_ALE_AGENT", columnList = "agent")
    }
)
@Getter
@Setter
public class AuditLogEntry {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "operator_id", length = 255, nullable = false)
    private String operatorId;

    @Column(name = "operator_role", length = 50, nullable = false)
    private String operatorRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", length = 50, nullable = false)
    private AuditActionType actionType;

    @CreationTimestamp
    @Column(name = "timestamp", nullable = false, updatable = false)
    private Instant timestamp;

    /** Soft reference to Release Flow (nullable – no FK constraint). */
    @Column(name = "release_flow_id", length = 36)
    private String releaseFlowId;

    /** Soft reference to Request (nullable – no FK constraint). */
    @Column(name = "request_id", length = 36)
    private String requestId;

    /** Soft reference to Task (nullable – no FK constraint). */
    @Column(name = "task_id", length = 36)
    private String taskId;

    /** Optional scope fields for multi-tenant-ish filtering and traceability. */
    @Column(name = "application", length = 255)
    private String application;

    @Column(name = "snow_group", length = 255)
    private String snowGroup;

    @Column(name = "agent", length = 255)
    private String agent;

    /**
     * Arbitrary JSON context for the action.
     * e.g. { fileName, importedCount } for upload; { field, oldValue, newValue } for edit.
     */
    @Convert(converter = JsonAttributeConverter.class)
    @Column(name = "context_payload", columnDefinition = "CLOB")
    private Map<String, Object> contextPayload;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }
}
