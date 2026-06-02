package com.wwa.agenthub.domain.skillhub;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "SKILL_HUB_SKILL_VERSION")
@Getter
@Setter
public class SkillHubSkillVersion {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private SkillHubSkill skill;

    @Column(name = "version_label", length = 40, nullable = false)
    private String version;

    @Column(name = "version_notes", length = 2000)
    private String versionNotes;

    @Column(name = "source_path", length = 500, nullable = false)
    private String sourcePath;

    @Lob
    @Column(name = "content_snapshot", columnDefinition = "CLOB", nullable = false)
    private String contentSnapshot;

    @Column(name = "content_sha256", length = 64, nullable = false)
    private String contentSha256;

    @Column(name = "created_by", length = 255, nullable = false, updatable = false)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
