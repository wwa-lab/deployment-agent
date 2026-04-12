package com.wwa.deploymentagent.domain.audit;

import com.wwa.deploymentagent.contracts.enums.ActorKind;
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
        @Index(name = "IDX_ALE_AGENT", columnList = "agent"),
        @Index(name = "IDX_ALE_CORRELATION", columnList = "correlation_id")
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

    /**
     * MVP Foundation Seam — actor kind. Always {@link ActorKind#HUMAN} in MVP.
     * Reserved for future policy / AI-assisted / system writes so the audit
     * trail can answer "who (or what) actually authorized this" retroactively.
     * See {@code docs/04-architecture/architecture.md} §MVP Foundation Seams.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "actor_kind", length = 20, nullable = false)
    private ActorKind actorKind = ActorKind.HUMAN;

    /**
     * MVP Foundation Seam — opaque reference describing the actor when it is
     * not the human operator (e.g. {@code policy:auto-sit-approve}, {@code ai:claude-opus-4-6#session-xyz}).
     * Null for {@link ActorKind#HUMAN}; the operator_id column carries the user id.
     */
    @Column(name = "actor_ref", length = 255)
    private String actorRef;

    /**
     * Request-scoped correlation ID set by {@code CorrelationIdFilter} and
     * read from SLF4J MDC at write time. Null for entries produced by
     * background jobs that run outside an HTTP request context.
     *
     * <p>This is infrastructure debt, not an MVP Foundation Seam — it is
     * read and written today for every inbound request. Its purpose is to
     * let operators stitch a single user action across server logs, audit
     * entries, and downstream Jenkins/Ansible submissions.
     */
    @Column(name = "correlation_id", length = 64)
    private String correlationId;

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

    /**
     * Name of the agent workspace that produced this entry.
     * e.g. "deployment-agent". Satisfies the platform audit standard's {@code agentName} field.
     * The legacy free-text {@code agent} column from the original schema is superseded by this field.
     */
    @Column(name = "agent_name", length = 255)
    private String agentName;

    /**
     * The type of the primary business object affected.
     * e.g. "ReleaseFlow", "Task", "AccessGrant". Platform audit standard: {@code targetType}.
     */
    @Column(name = "target_type", length = 100)
    private String targetType;

    /**
     * The ID of the primary business object affected. Platform audit standard: {@code targetId}.
     */
    @Column(name = "target_id", length = 36)
    private String targetId;

    /**
     * The system that triggered this action.
     * e.g. "wwa-frontend", "wwa-api", "jenkins-callback". Platform audit standard: {@code sourceSystem}.
     */
    @Column(name = "source_system", length = 100)
    private String sourceSystem;

    /** Legacy free-text agent field. Superseded by {@code agentName}. Retained for backward compatibility. */
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
