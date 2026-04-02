package com.wwa.deploymentagent.domain.developmentspec;

import com.wwa.deploymentagent.contracts.enums.DevelopmentSpecStatus;
import com.wwa.deploymentagent.util.JsonAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(
        name = "DA_DEVELOPMENT_SPEC",
        indexes = {
                @Index(name = "IDX_DSPEC_SCOPE", columnList = "application, snow_group"),
                @Index(name = "IDX_DSPEC_STATUS", columnList = "status")
        }
)
@Getter
@Setter
public class DevelopmentSpec {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "module_name", length = 255)
    private String moduleName;

    @Column(name = "program_type", length = 50, nullable = false)
    private String programType;

    @Column(name = "code_style", length = 50, nullable = false)
    private String codeStyle;

    @Column(name = "application", length = 255)
    private String application;

    @Column(name = "snow_group", length = 255)
    private String snowGroup;

    @Column(name = "source_payload", columnDefinition = "CLOB", nullable = false)
    @Convert(converter = JsonAttributeConverter.class)
    private Map<String, Object> sourcePayload = new LinkedHashMap<>();

    @Column(name = "generated_payload", columnDefinition = "CLOB")
    @Convert(converter = JsonAttributeConverter.class)
    private Map<String, Object> generatedPayload;

    @Column(name = "generated_content", columnDefinition = "CLOB")
    private String generatedContent;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @Column(name = "generated_by", length = 255)
    private String generatedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private DevelopmentSpecStatus status = DevelopmentSpecStatus.DRAFT;

    @Column(name = "created_by", length = 255, nullable = false, updatable = false)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_by", length = 255, nullable = false)
    private String updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }
}
