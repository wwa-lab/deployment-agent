package com.wwa.agenthub.contracts.dto;

import com.wwa.agenthub.contracts.enums.SkillStatus;
import com.wwa.agenthub.domain.skillhub.SkillHubSkill;
import com.wwa.agenthub.domain.skillhub.SkillHubSkillVersion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record SkillHubSkillDto(
        String id,
        String name,
        String description,
        String category,
        List<String> tags,
        String owner,
        SkillStatus status,
        String currentVersion,
        String versionNotes,
        String contentSourceType,
        String sourcePath,
        String contentSha256,
        String currentVersionId,
        Instant lastIndexedAt,
        String currentContentSnapshot,
        List<VersionSummary> versions,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt
) {
    public static SkillHubSkillDto from(SkillHubSkill skill) {
        return from(skill, null, List.of());
    }

    public static SkillHubSkillDto from(SkillHubSkill skill, String currentContentSnapshot, List<SkillHubSkillVersion> versions) {
        return new SkillHubSkillDto(
                skill.getId(),
                skill.getName(),
                skill.getDescription(),
                skill.getCategory(),
                List.copyOf(skill.getTags()),
                skill.getOwner(),
                skill.getStatus(),
                skill.getCurrentVersion(),
                skill.getVersionNotes(),
                skill.getContentSourceType(),
                skill.getSourcePath(),
                skill.getContentSha256(),
                skill.getCurrentVersionId(),
                skill.getLastIndexedAt(),
                currentContentSnapshot,
                versions.stream().map(VersionSummary::from).toList(),
                skill.getCreatedBy(),
                skill.getCreatedAt(),
                skill.getUpdatedBy(),
                skill.getUpdatedAt()
        );
    }

    public record UpsertRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 2000) String description,
            @NotBlank @Size(max = 80) String category,
            List<@NotBlank @Size(max = 60) String> tags,
            @NotBlank @Size(max = 160) String owner,
            SkillStatus status,
            @NotBlank @Size(max = 40) String currentVersion,
            @Size(max = 2000) String versionNotes,
            @Size(max = 500) String sourcePath,
            @Size(max = 100000) String content
    ) {}

    public record VersionCreateRequest(
            @NotBlank @Size(max = 40) String version,
            @Size(max = 2000) String versionNotes,
            @Size(max = 100000) String content
    ) {}

    public record VersionSummary(
            String id,
            String version,
            String versionNotes,
            String sourcePath,
            String contentSha256,
            String createdBy,
            Instant createdAt
    ) {
        public static VersionSummary from(SkillHubSkillVersion version) {
            return new VersionSummary(
                    version.getId(),
                    version.getVersion(),
                    version.getVersionNotes(),
                    version.getSourcePath(),
                    version.getContentSha256(),
                    version.getCreatedBy(),
                    version.getCreatedAt()
            );
        }
    }

    public record VersionDetail(
            String id,
            String skillId,
            String version,
            String versionNotes,
            String sourcePath,
            String contentSnapshot,
            String contentSha256,
            String createdBy,
            Instant createdAt
    ) {
        public static VersionDetail from(SkillHubSkillVersion version) {
            return new VersionDetail(
                    version.getId(),
                    version.getSkill().getId(),
                    version.getVersion(),
                    version.getVersionNotes(),
                    version.getSourcePath(),
                    version.getContentSnapshot(),
                    version.getContentSha256(),
                    version.getCreatedBy(),
                    version.getCreatedAt()
            );
        }
    }
}
