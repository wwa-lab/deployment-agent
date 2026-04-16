package com.wwa.agenthub.domain.configuration;

import com.wwa.agenthub.contracts.enums.ConfigKey;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * ConfigurationItem – key/value pairs for system integration configuration.
 *
 * <p>Primary key is the config key string (no separate surrogate ID).
 * Changes apply to future executions only (locked design decision).
 * No @Version column – config updates are authoritative overwrites by DevOps Admin.
 */
@Entity
@Table(name = "DA_CONFIGURATION_ITEM")
@Getter
@Setter
public class ConfigurationItem {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "config_key", length = 100, nullable = false)
    private ConfigKey configKey;

    @Column(name = "config_value", length = 2000, nullable = false)
    private String configValue;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "updated_by", length = 255, nullable = false)
    private String updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
