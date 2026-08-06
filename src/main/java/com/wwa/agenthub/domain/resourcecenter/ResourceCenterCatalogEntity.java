package com.wwa.agenthub.domain.resourcecenter;

import com.wwa.agenthub.domain.resourcecenter.model.DirectoryScope;
import com.wwa.agenthub.util.DirectoryScopeListJsonAttributeConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "DA_SERVICE_DIRECTORY_CATALOG")
@Getter
@Setter
public class ResourceCenterCatalogEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "payload", columnDefinition = "CLOB", nullable = false)
    @Convert(converter = DirectoryScopeListJsonAttributeConverter.class)
    private List<DirectoryScope> payload = new ArrayList<>();

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (payload == null) {
            payload = new ArrayList<>();
        }
    }
}
