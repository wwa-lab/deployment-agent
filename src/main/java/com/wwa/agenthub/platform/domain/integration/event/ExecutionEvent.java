package com.wwa.agenthub.platform.domain.integration.event;

import com.wwa.agenthub.contracts.enums.ActorKind;
import com.wwa.agenthub.contracts.enums.ExecutionEventType;
import com.wwa.agenthub.domain.task.Task;
import com.wwa.agenthub.domain.task.TaskExecutionHistory;
import com.wwa.agenthub.util.JsonAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.util.Map;
import java.util.UUID;

@Entity
@Table(
        name = "DA_EXECUTION_EVENT",
        indexes = {
                @Index(name = "IDX_EE_EXEC_RECEIVED", columnList = "execution_id, received_at"),
                @Index(name = "IDX_EE_CORRELATION", columnList = "correlation_id"),
                @Index(name = "IDX_EE_EXEC_SEQUENCE", columnList = "execution_id, sequence_number")
        }
)
@Getter
@Setter
public class ExecutionEvent {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execution_id", nullable = false)
    private TaskExecutionHistory execution;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 40, nullable = false)
    private ExecutionEventType eventType;

    @Column(name = "sequence_number")
    private Long sequenceNumber;

    @Column(name = "percentage")
    private Integer percentage;

    @Column(name = "message", length = 2000)
    private String message;

    @Convert(converter = JsonAttributeConverter.class)
    @Column(name = "details_json", columnDefinition = "CLOB")
    private Map<String, Object> details;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_kind", length = 20, nullable = false)
    private ActorKind actorKind = ActorKind.HUMAN;

    @Column(name = "actor_id", length = 255, nullable = false)
    private String actorId;

    @Column(name = "client_application_id", length = 255, nullable = false)
    private String clientApplicationId;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "client_timestamp")
    private Instant clientTimestamp;

    @CreationTimestamp
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }
}
