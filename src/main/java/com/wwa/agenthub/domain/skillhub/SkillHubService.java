package com.wwa.agenthub.domain.skillhub;

import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.dto.SkillHubSkillDto;
import com.wwa.agenthub.contracts.enums.AuditActionType;
import com.wwa.agenthub.contracts.enums.SkillStatus;
import com.wwa.agenthub.domain.audit.AuditLoggerService;
import com.wwa.agenthub.errors.NotFoundAppException;
import com.wwa.agenthub.errors.ValidationAppException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class SkillHubService {

    private final SkillHubSkillRepository repository;
    private final SkillHubSkillVersionRepository versionRepository;
    private final SkillHubContentSourceService contentSourceService;
    private final AuditLoggerService auditLogger;

    public SkillHubService(
            SkillHubSkillRepository repository,
            SkillHubSkillVersionRepository versionRepository,
            SkillHubContentSourceService contentSourceService,
            AuditLoggerService auditLogger
    ) {
        this.repository = repository;
        this.versionRepository = versionRepository;
        this.contentSourceService = contentSourceService;
        this.auditLogger = auditLogger;
    }

    @Transactional(readOnly = true)
    public Page<SkillHubSkill> list(String query, String category, SkillStatus status, Pageable pageable) {
        String normalizedQuery = normalizeSearch(query);
        String normalizedCategory = normalizeSearch(category);
        List<SkillHubSkill> filtered = repository.findAll(Sort.by(Sort.Direction.DESC, "updatedAt")).stream()
                .filter(skill -> status == null || skill.getStatus() == status)
                .filter(skill -> normalizedCategory == null
                        || skill.getCategory().toLowerCase(Locale.ROOT).equals(normalizedCategory))
                .filter(skill -> normalizedQuery == null || matchesQuery(skill, normalizedQuery))
                .toList();

        int start = Math.toIntExact(pageable.getOffset());
        if (start >= filtered.size()) {
            return new PageImpl<>(List.of(), pageable, filtered.size());
        }
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        return new PageImpl<>(filtered.subList(start, end), pageable, filtered.size());
    }

    @Transactional(readOnly = true)
    public SkillHubSkill get(String id) {
        return repository.findById(normalizeId(id))
                .orElseThrow(() -> new NotFoundAppException("SkillHubSkill", id));
    }

    @Transactional(readOnly = true)
    public SkillHubSkillDto getDetail(String id) {
        SkillHubSkill skill = get(id);
        List<SkillHubSkillVersion> versions = versionRepository.findBySkillIdOrderByCreatedAtDesc(skill.getId());
        String currentContent = versions.stream()
                .filter(version -> version.getId().equals(skill.getCurrentVersionId()))
                .findFirst()
                .map(SkillHubSkillVersion::getContentSnapshot)
                .orElse(null);
        return SkillHubSkillDto.from(skill, currentContent, versions);
    }

    @Transactional(readOnly = true)
    public SkillHubSkillVersion getVersion(String skillId, String versionId) {
        get(skillId);
        return versionRepository.findByIdAndSkillId(normalizeId(versionId), normalizeId(skillId))
                .orElseThrow(() -> new NotFoundAppException("SkillHubSkillVersion", versionId));
    }

    @Transactional
    public SkillHubSkill create(SkillHubSkillDto.UpsertRequest request, UserContext user) {
        SkillHubSkill skill = new SkillHubSkill();
        skill.setId(UUID.randomUUID().toString());
        applyRequest(skill, request, false);
        skill.setCreatedBy(user.userId());
        skill.setUpdatedBy(user.userId());

        SkillHubContentSourceService.SkillContentSnapshot snapshot = contentSourceService.createSkillFile(
                skill.getId(),
                skill.getName(),
                skill.getDescription(),
                skill.getCategory(),
                skill.getTags(),
                skill.getOwner(),
                skill.getStatus().name(),
                request.currentVersion(),
                request.versionNotes(),
                request.content(),
                user.userId()
        );
        skill.setContentSourceType(snapshot.contentSourceType());
        skill.setSourcePath(snapshot.sourcePath());
        skill.setContentSha256(snapshot.contentSha256());
        try {
            SkillHubSkill saved = repository.saveAndFlush(skill);
            createVersionSnapshot(saved, request.currentVersion(), request.versionNotes(), snapshot, user);
            auditLogger.log(user, AuditActionType.skill_hub_create, buildCreateAuditContext(saved));
            return saved;
        } catch (RuntimeException ex) {
            contentSourceService.deleteSkillFileIfExists(snapshot.sourcePath());
            throw ex;
        }
    }

    @Transactional
    public SkillHubSkill update(String id, SkillHubSkillDto.UpsertRequest request, UserContext user) {
        SkillHubSkill skill = get(id);
        Map<String, Object> previous = snapshot(skill);

        applyRequest(skill, request, true);
        skill.setUpdatedBy(user.userId());

        SkillHubSkill saved = repository.save(skill);
        auditLogger.log(user, AuditActionType.skill_hub_update, buildUpdateAuditContext(saved, previous));
        return saved;
    }

    @Transactional
    public SkillHubSkillVersion createVersion(String id, SkillHubSkillDto.VersionCreateRequest request, UserContext user) {
        SkillHubSkill skill = get(id);
        String previousFileContent = contentSourceService.readSkillFile(skill.getSourcePath());
        SkillHubContentSourceService.SkillContentSnapshot snapshot = contentSourceService.appendVersion(
                skill.getSourcePath(),
                requireText(request.version(), "Skill version is required."),
                normalizeBlank(request.versionNotes()),
                request.content(),
                user.userId()
        );
        try {
            SkillHubSkillVersion version = createVersionSnapshot(
                    skill,
                    requireText(request.version(), "Skill version is required."),
                    normalizeBlank(request.versionNotes()),
                    snapshot,
                    user
            );
            auditLogger.log(user, AuditActionType.skill_hub_version_create, buildVersionAuditContext(skill, version));
            return version;
        } catch (RuntimeException ex) {
            contentSourceService.restoreSkillFile(skill.getSourcePath(), previousFileContent);
            throw ex;
        }
    }

    private void applyRequest(SkillHubSkill skill, SkillHubSkillDto.UpsertRequest request, boolean allowSourcePathUpdate) {
        skill.setName(requireText(request.name(), "Skill name is required."));
        skill.setDescription(requireText(request.description(), "Skill description is required."));
        skill.setCategory(requireText(request.category(), "Skill category is required."));
        skill.setTags(normalizeTags(request.tags()));
        skill.setOwner(requireText(request.owner(), "Skill owner is required."));
        skill.setStatus(request.status() == null ? SkillStatus.DRAFT : request.status());
        skill.setCurrentVersion(requireText(request.currentVersion(), "Skill current version is required."));
        skill.setVersionNotes(normalizeBlank(request.versionNotes()));
        skill.setContentSourceType("FILE_PATH");
        if (allowSourcePathUpdate && normalizeBlank(request.sourcePath()) != null) {
            skill.setSourcePath(contentSourceService.validateSourcePath(request.sourcePath()));
        }
    }

    private SkillHubSkillVersion createVersionSnapshot(
            SkillHubSkill skill,
            String versionLabel,
            String versionNotes,
            SkillHubContentSourceService.SkillContentSnapshot snapshot,
            UserContext user
    ) {
        SkillHubSkillVersion version = new SkillHubSkillVersion();
        version.setId(UUID.randomUUID().toString());
        version.setSkill(skill);
        version.setVersion(requireText(versionLabel, "Skill version is required."));
        version.setVersionNotes(normalizeBlank(versionNotes));
        version.setSourcePath(snapshot.sourcePath());
        version.setContentSnapshot(snapshot.contentSnapshot());
        version.setContentSha256(snapshot.contentSha256());
        version.setCreatedBy(user.userId());

        SkillHubSkillVersion savedVersion = versionRepository.saveAndFlush(version);
        skill.setContentSourceType(snapshot.contentSourceType());
        skill.setSourcePath(snapshot.sourcePath());
        skill.setContentSha256(snapshot.contentSha256());
        skill.setCurrentVersion(savedVersion.getVersion());
        skill.setVersionNotes(savedVersion.getVersionNotes());
        skill.setCurrentVersionId(savedVersion.getId());
        skill.setLastIndexedAt(savedVersion.getCreatedAt());
        skill.setUpdatedBy(user.userId());
        repository.save(skill);
        return savedVersion;
    }

    private boolean matchesQuery(SkillHubSkill skill, String query) {
        return contains(skill.getName(), query)
                || contains(skill.getDescription(), query)
                || contains(skill.getCategory(), query)
                || contains(skill.getOwner(), query)
                || contains(skill.getCurrentVersion(), query)
                || skill.getTags().stream().anyMatch(tag -> contains(tag, query));
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String tag : tags) {
            String value = normalizeBlank(tag);
            if (value != null) {
                normalized.add(value);
            }
        }
        return List.copyOf(normalized);
    }

    private String requireText(String value, String message) {
        String normalized = normalizeBlank(value);
        if (normalized == null) {
            throw new ValidationAppException(message);
        }
        return normalized;
    }

    private String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeSearch(String value) {
        String normalized = normalizeBlank(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeId(String id) {
        return requireText(id, "Skill ID is required.");
    }

    private Map<String, Object> snapshot(SkillHubSkill skill) {
        LinkedHashMap<String, Object> context = new LinkedHashMap<>();
        context.put("name", skill.getName());
        context.put("description", skill.getDescription());
        context.put("category", skill.getCategory());
        context.put("tags", List.copyOf(skill.getTags()));
        context.put("owner", skill.getOwner());
        context.put("status", skill.getStatus().name());
        context.put("currentVersion", skill.getCurrentVersion());
        context.put("sourcePath", skill.getSourcePath());
        context.put("versionNotes", skill.getVersionNotes());
        return context;
    }

    private Map<String, Object> buildCreateAuditContext(SkillHubSkill skill) {
        LinkedHashMap<String, Object> context = new LinkedHashMap<>();
        context.put("skillId", skill.getId());
        context.put("name", skill.getName());
        context.put("category", skill.getCategory());
        context.put("status", skill.getStatus().name());
        context.put("currentVersion", skill.getCurrentVersion());
        context.put("sourcePath", skill.getSourcePath());
        context.put("application", "WWA Platform");
        context.put("snowGroup", "WWA Platform");
        context.put("agent", "Skill Hub");
        return context;
    }

    private Map<String, Object> buildUpdateAuditContext(SkillHubSkill skill, Map<String, Object> previous) {
        LinkedHashMap<String, Object> context = new LinkedHashMap<>();
        context.put("skillId", skill.getId());
        context.put("old", previous);
        context.put("new", snapshot(skill));
        context.put("application", "WWA Platform");
        context.put("snowGroup", "WWA Platform");
        context.put("agent", "Skill Hub");
        return context;
    }

    private Map<String, Object> buildVersionAuditContext(SkillHubSkill skill, SkillHubSkillVersion version) {
        LinkedHashMap<String, Object> context = new LinkedHashMap<>();
        context.put("skillId", skill.getId());
        context.put("versionId", version.getId());
        context.put("version", version.getVersion());
        context.put("sourcePath", version.getSourcePath());
        context.put("contentSha256", version.getContentSha256());
        context.put("application", "WWA Platform");
        context.put("snowGroup", "WWA Platform");
        context.put("agent", "Skill Hub");
        return context;
    }
}
