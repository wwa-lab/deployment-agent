package com.wwa.deploymentagent.domain.configuration;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Built-in integration component configuration shown in Configuration Management.
 *
 * <p>Each row represents one scoped instance of a built-in tool integration
 * (Jenkins, Ansible, callback). Multiple rows can exist for the same built-in
 * component so runtime resolution can fall back from agent-specific overrides to
 * platform defaults.
 */
@Entity
@Table(
        name = "DA_CONFIGURATION_COMPONENT",
        indexes = {
                @Index(name = "IDX_DCC_COMPONENT", columnList = "component_id"),
                @Index(name = "IDX_DCC_SYSTEM_TYPE", columnList = "system_type"),
                @Index(name = "IDX_DCC_SCOPE", columnList = "application, snow_group, agent"),
                @Index(name = "UK_DCC_COMPONENT_SCOPE", columnList = "component_id, scope_key", unique = true)
        }
)
@Getter
@Setter
public class ConfigurationComponent {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    /**
     * Stable built-in component identifier such as {@code jenkins} or
     * {@code ansible}. Multiple scoped instances may share the same value.
     */
    @Column(name = "component_id", length = 50, nullable = false)
    private String componentId;

    @Column(name = "scope_key", length = 500, nullable = false)
    private String scopeKey;

    @Column(name = "system_type", length = 30, nullable = false)
    private String systemType;

    @Column(name = "display_name", length = 255, nullable = false)
    private String displayName;

    @Column(name = "area", length = 100, nullable = false)
    private String area;

    @Column(name = "application", length = 255)
    private String application;

    @Column(name = "snow_group", length = 255)
    private String snowGroup;

    @Column(name = "agent", length = 255)
    private String agent;

    @Column(name = "service_endpoint", length = 2000)
    private String serviceEndpoint;

    @Column(name = "service_user", length = 255)
    private String serviceUser;

    @Column(name = "credential_value", length = 4000)
    private String credentialValue;

    @Column(name = "track_service_user", nullable = false)
    private boolean trackServiceUser;

    @Column(name = "track_credential", nullable = false)
    private boolean trackCredential;

    @Column(name = "description", length = 500)
    private String description;

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
