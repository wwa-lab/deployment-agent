package com.wwa.agenthub.web;

import com.wwa.agenthub.contracts.enums.AuditActionType;
import com.wwa.agenthub.contracts.enums.SkillStatus;
import com.wwa.agenthub.domain.audit.AuditLogEntry;
import com.wwa.agenthub.domain.audit.AuditLogRepository;
import com.wwa.agenthub.domain.skillhub.SkillHubSkill;
import com.wwa.agenthub.domain.skillhub.SkillHubSkillRepository;
import com.wwa.agenthub.domain.skillhub.SkillHubSkillVersionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("SkillHubController API contract")
class SkillHubControllerTest {

    private static final String BASE = "/api/platform/skill-hub/skills";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SkillHubSkillRepository skillRepository;

    @Autowired
    private SkillHubSkillVersionRepository versionRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    @DisplayName("list returns skills sorted by updated time")
    void list_returnsSkillsSortedByUpdatedTime() throws Exception {
        SkillHubSkill older = saveSkill("Code Review", "Engineering", SkillStatus.ACTIVE, "1.0.0", "review");
        SkillHubSkill newer = saveSkill("Release Coach", "Delivery", SkillStatus.DRAFT, "0.2.0", "release");
        older.setDescription("Reviews code changes");
        skillRepository.save(older);
        newer.setDescription("Guides release readiness");
        skillRepository.save(newer);

        mockMvc.perform(get(BASE)
                        .header("X-User-Id", "emp-001")
                        .header("X-User-Role", "DEVELOPER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.data[0].name").value("Release Coach"))
                .andExpect(jsonPath("$.data[1].name").value("Code Review"));
    }

    @Test
    @DisplayName("list filters by search, category, and status")
    void list_filtersBySearchCategoryAndStatus() throws Exception {
        saveSkill("Code Review", "Engineering", SkillStatus.ACTIVE, "1.0.0", "quality");
        saveSkill("Prompt Writer", "Knowledge", SkillStatus.DEPRECATED, "0.8.0", "prompt");

        mockMvc.perform(get(BASE)
                        .param("query", "quality")
                        .param("category", "Engineering")
                        .param("status", "ACTIVE")
                        .header("X-User-Id", "emp-001")
                        .header("X-User-Role", "DEVELOPER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Code Review"))
                .andExpect(jsonPath("$.data[0].tags[0]").value("quality"));
    }

    @Test
    @DisplayName("create persists skill and writes audit")
    void create_persistsSkillAndAudits() throws Exception {
        mockMvc.perform(post(BASE)
                        .header("X-User-Id", "emp-002")
                        .header("X-User-Role", "DEVELOPER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Skill Curator",
                                  "description": "Maintains skill registry quality.",
                                  "category": "Governance",
                                  "tags": ["registry", "registry", "quality"],
                                  "owner": "Platform Team",
                                  "status": "ACTIVE",
                                  "currentVersion": "1.0.0",
                                  "versionNotes": "Initial metadata entry",
                                  "content": "Use this skill to maintain registry quality."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.name").value("Skill Curator"))
                .andExpect(jsonPath("$.tags.length()").value(2))
                .andExpect(jsonPath("$.sourcePath").isString())
                .andExpect(jsonPath("$.contentSha256").isString())
                .andExpect(jsonPath("$.currentVersionId").isString())
                .andExpect(jsonPath("$.createdBy").value("emp-002"))
                .andExpect(jsonPath("$.updatedBy").value("emp-002"));

        assertThat(skillRepository.findAll()).hasSize(1);
        assertThat(versionRepository.findAll()).hasSize(1);
        SkillHubSkill saved = skillRepository.findAll().getFirst();
        assertThat(Files.readString(Path.of(saved.getSourcePath()))).contains("Version 1.0.0");
        assertThat(Files.readString(Path.of(saved.getSourcePath()))).contains("Use this skill to maintain registry quality.");
        AuditLogEntry audit = auditLogRepository.findByActionType(
                        AuditActionType.skill_hub_create,
                        PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "timestamp")))
                .getContent()
                .getFirst();
        assertThat(audit.getOperatorId()).isEqualTo("emp-002");
        assertThat(audit.getContextPayload()).containsEntry("name", "Skill Curator");
    }

    @Test
    @DisplayName("update changes metadata, version, updater, and audit")
    void update_changesMetadataVersionUpdaterAndAudit() throws Exception {
        SkillHubSkill skill = saveSkill("Skill Curator", "Governance", SkillStatus.DRAFT, "0.1.0", "registry");

        mockMvc.perform(put(BASE + "/" + skill.getId())
                        .header("X-User-Id", "emp-003")
                        .header("X-User-Role", "TL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Skill Curator",
                                  "description": "Maintains and reviews skill metadata.",
                                  "category": "Platform",
                                  "tags": ["registry", "review"],
                                  "owner": "Enablement Team",
                                  "status": "ACTIVE",
                                  "currentVersion": "1.0.0",
                                  "versionNotes": "Promoted for platform use",
                                  "content": "Updated content is ignored for metadata-only edits."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("Platform"))
                .andExpect(jsonPath("$.owner").value("Enablement Team"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.currentVersion").value("1.0.0"))
                .andExpect(jsonPath("$.updatedBy").value("emp-003"));

        SkillHubSkill updated = skillRepository.findById(skill.getId()).orElseThrow();
        assertThat(updated.getVersionNotes()).isEqualTo("Promoted for platform use");
        assertThat(versionRepository.findAll()).isEmpty();

        AuditLogEntry audit = auditLogRepository.findByActionType(
                        AuditActionType.skill_hub_update,
                        PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "timestamp")))
                .getContent()
                .getFirst();
        assertThat(audit.getContextPayload()).containsEntry("skillId", skill.getId());
    }

    @Test
    @DisplayName("create rejects missing required fields")
    void create_rejectsMissingRequiredFields() throws Exception {
        mockMvc.perform(post(BASE)
                        .header("X-User-Id", "emp-002")
                        .header("X-User-Role", "DEVELOPER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "description": "Missing category and version.",
                                  "owner": "Platform Team"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("update rejects unsafe source path overrides")
    void update_rejectsUnsafeSourcePath() throws Exception {
        SkillHubSkill skill = saveSkill("Unsafe Skill", "Governance", SkillStatus.DRAFT, "0.1.0", "registry");

        mockMvc.perform(put(BASE + "/" + skill.getId())
                        .header("X-User-Id", "emp-002")
                        .header("X-User-Role", "DEVELOPER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Unsafe Skill",
                                  "description": "Invalid source.",
                                  "category": "Governance",
                                  "tags": ["registry"],
                                  "owner": "Platform Team",
                                  "status": "ACTIVE",
                                  "currentVersion": "1.0.0",
                                  "versionNotes": "Initial metadata entry",
                                  "sourcePath": "../outside/SKILL.md",
                                  "content": "Unsafe path override."
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("detail returns current content snapshot and version history")
    void detail_returnsCurrentContentAndVersionHistory() throws Exception {
        String skillId = createSkillThroughApi("1.0.0", "Initial skill content.");

        mockMvc.perform(post(BASE + "/" + skillId + "/versions")
                        .header("X-User-Id", "emp-004")
                        .header("X-User-Role", "DEVELOPER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": "1.1.0",
                                  "versionNotes": "Captured updated user stories.",
                                  "content": "Second version skill content."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("1.1.0"))
                .andExpect(jsonPath("$.sourcePath").isString())
                .andExpect(jsonPath("$.contentSnapshot").isString())
                .andExpect(jsonPath("$.contentSha256").isString());

        mockMvc.perform(get(BASE + "/" + skillId)
                        .header("X-User-Id", "emp-001")
                        .header("X-User-Role", "DEVELOPER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentVersion").value("1.1.0"))
                .andExpect(jsonPath("$.currentContentSnapshot").isString())
                .andExpect(jsonPath("$.versions.length()").value(2))
                .andExpect(jsonPath("$.versions[0].version").value("1.1.0"));

        SkillHubSkill updated = skillRepository.findById(skillId).orElseThrow();
        String skillFile = Files.readString(Path.of(updated.getSourcePath()));
        assertThat(skillFile).contains("Version 1.0.0");
        assertThat(skillFile).contains("Version 1.1.0");
        assertThat(skillFile).contains("Second version skill content.");

        AuditLogEntry audit = auditLogRepository.findByActionType(
                        AuditActionType.skill_hub_version_create,
                        PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "timestamp")))
                .getContent()
                .getFirst();
        assertThat(audit.getOperatorId()).isEqualTo("emp-004");
        assertThat(audit.getContextPayload()).containsEntry("version", "1.1.0");
    }

    @Test
    @DisplayName("version detail returns historical content snapshot")
    void versionDetail_returnsHistoricalSnapshot() throws Exception {
        String skillId = createSkillThroughApi("1.0.0", "Historical content.");
        String versionId = skillRepository.findById(skillId).orElseThrow().getCurrentVersionId();

        mockMvc.perform(get(BASE + "/" + skillId + "/versions/" + versionId)
                        .header("X-User-Id", "emp-001")
                        .header("X-User-Role", "DEVELOPER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(versionId))
                .andExpect(jsonPath("$.skillId").value(skillId))
                .andExpect(jsonPath("$.version").value("1.0.0"))
                .andExpect(jsonPath("$.contentSnapshot").isString());
    }

    @Test
    @DisplayName("create requires authentication")
    void create_requiresAuthentication() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("guest write is forbidden by read-only guard")
    void guestWrite_isForbidden() throws Exception {
        mockMvc.perform(post(BASE)
                        .header("X-User-Id", "guest")
                        .header("X-User-Role", "GUEST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isForbidden());
    }

    private SkillHubSkill saveSkill(String name, String category, SkillStatus status, String version, String tag) {
        SkillHubSkill skill = new SkillHubSkill();
        skill.setId(UUID.randomUUID().toString());
        skill.setName(name);
        skill.setDescription(name + " description");
        skill.setCategory(category);
        skill.setTags(List.of(tag));
        skill.setOwner("Platform Team");
        skill.setStatus(status);
        skill.setCurrentVersion(version);
        skill.setCreatedBy("seed");
        skill.setUpdatedBy("seed");
        skill.setContentSourceType("FILE_PATH");
        skill.setSourcePath("README.md");
        return skillRepository.saveAndFlush(skill);
    }

    private String validPayload() {
        return """
                {
                  "name": "Skill Curator",
                  "description": "Maintains skill registry quality.",
                  "category": "Governance",
                  "tags": ["registry"],
                  "owner": "Platform Team",
                  "status": "ACTIVE",
                  "currentVersion": "1.0.0",
                  "versionNotes": "Initial metadata entry",
                  "content": "Use this skill to maintain registry quality."
                }
                """;
    }

    private String createSkillThroughApi(String version, String content) throws Exception {
        String response = mockMvc.perform(post(BASE)
                        .header("X-User-Id", "emp-002")
                        .header("X-User-Role", "DEVELOPER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Skill Curator",
                                  "description": "Maintains skill registry quality.",
                                  "category": "Governance",
                                  "tags": ["registry"],
                                  "owner": "Platform Team",
                                  "status": "ACTIVE",
                                  "currentVersion": "%s",
                                  "versionNotes": "Initial metadata entry",
                                  "content": "%s"
                                }
                                """.formatted(version, content)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return response.replaceFirst(".*\"id\":\"([^\"]+)\".*", "$1");
    }
}
