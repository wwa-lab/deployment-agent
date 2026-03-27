package com.wwa.deploymentagent.domain.configuration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Built-in integration component configuration shown in Configuration Management.
 *
 * <p>Each row represents one tool integration (Jenkins, Ansible, callback) and
 * acts as the source of truth for both the component workspace and the raw
 * configuration table derived from it.
 */
@Entity
@Table(name = "DA_CONFIGURATION_COMPONENT")
@Getter
@Setter
public class ConfigurationComponent {

    @Id
    @Column(name = "component_id", length = 50, nullable = false)
    private String componentId;

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
}
