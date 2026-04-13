package com.wwa.agenthub.domain.configuration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "DA_SCOPE_DIRECTORY",
        indexes = {
                @Index(name = "IDX_DSD_APPLICATION", columnList = "application"),
                @Index(name = "IDX_DSD_APP_SNOW", columnList = "application, snow_group"),
                @Index(name = "IDX_DSD_APP_SNOW_AGENT", columnList = "application, snow_group, agent"),
                @Index(name = "UK_DSD_SCOPE_KEY", columnList = "scope_key", unique = true)
        }
)
@Getter
@Setter
public class ScopeDirectoryEntry {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "scope_key", length = 500, nullable = false)
    private String scopeKey;

    @Column(name = "application", length = 255, nullable = false)
    private String application;

    @Column(name = "snow_group", length = 255)
    private String snowGroup;

    @Column(name = "agent", length = 255)
    private String agent;

    @Column(name = "updated_by", length = 255, nullable = false)
    private String updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }
}
