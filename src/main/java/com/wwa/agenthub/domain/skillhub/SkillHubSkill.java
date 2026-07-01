package com.wwa.agenthub.domain.skillhub;

import com.wwa.agenthub.contracts.enums.SkillStatus;
import com.wwa.agenthub.util.StringListJsonAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "SKILL_HUB_SKILL")
@Getter
@Setter
public class SkillHubSkill {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "name", length = 160, nullable = false)
    private String name;

    @Column(name = "description", length = 2000, nullable = false)
    private String description;

    @Column(name = "category", length = 80, nullable = false)
    private String category;

    @Column(name = "tags", columnDefinition = "CLOB", nullable = false)
    @Convert(converter = StringListJsonAttributeConverter.class)
    private List<String> tags = new ArrayList<>();

    @Column(name = "owner", length = 160, nullable = false)
    private String owner;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private SkillStatus status = SkillStatus.DRAFT;

    @Column(name = "current_version", length = 40, nullable = false)
    private String currentVersion;

    @Column(name = "version_notes", length = 2000)
    private String versionNotes;

    @Column(name = "content_source_type", length = 30, nullable = false)
    private String contentSourceType = "FILE_PATH";

    @Column(name = "source_path", length = 500, nullable = false)
    private String sourcePath;

    @Column(name = "content_sha256", length = 64)
    private String contentSha256;

    @Column(name = "current_version_id", length = 36)
    private String currentVersionId;

    @Column(name = "last_indexed_at")
    private Instant lastIndexedAt;

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
}
