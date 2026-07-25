package com.wwa.agenthub.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.wwa.agenthub.contracts.enums.AuditActionType;
import com.wwa.agenthub.domain.audit.AuditLogEntry;
import com.wwa.agenthub.domain.audit.AuditLogRepository;
import com.wwa.agenthub.domain.configuration.ConfigurationComponentRepository;
import com.wwa.agenthub.domain.configuration.ConfigurationRepository;
import com.wwa.agenthub.domain.configuration.ScopeDirectoryRepository;
import com.wwa.agenthub.domain.resourcecenter.ResourceCenterCatalogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("ResourceCenterController")
class ResourceCenterControllerTest {

    private static final String BASE = "/api/platform/resource-center";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ResourceCenterCatalogRepository catalogRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ConfigurationComponentRepository configurationComponentRepository;

    @Autowired
    private ConfigurationRepository configurationRepository;

    @Autowired
    private ScopeDirectoryRepository scopeDirectoryRepository;

    // ─── helpers ─────────────────────────────────────────────────────────────

    private static RequestPostProcessor adminHeaders() {
        return request -> {
            request.addHeader("X-User-Id", "emp-003");
            request.addHeader("X-User-Role", "DEVOPS_ADMIN");
            return request;
        };
    }

    private static RequestPostProcessor devHeaders() {
        return request -> {
            request.addHeader("X-User-Id", "emp-001");
            request.addHeader("X-User-Role", "DEVELOPER");
            return request;
        };
    }

    private static RequestPostProcessor guestHeaders() {
        return request -> {
            request.addHeader("X-User-Id", "guest");
            request.addHeader("X-User-Role", "GUEST");
            return request;
        };
    }

    private MvcResult readCatalogAsAdmin(boolean includeDisabled) throws Exception {
        return mockMvc.perform(get(BASE)
                        .param("includeDisabled", String.valueOf(includeDisabled))
                        .with(adminHeaders()))
                .andExpect(status().isOk())
                .andReturn();
    }

    private int currentVersion() throws Exception {
        MvcResult result = readCatalogAsAdmin(true);
        return JsonPath.read(result.getResponse().getContentAsString(), "$.version");
    }

    private String catalogJson(boolean includeDisabled) throws Exception {
        return readCatalogAsAdmin(includeDisabled).getResponse().getContentAsString();
    }

    private long resourceCenterAuditCount() {
        return auditLogRepository.findAll().stream()
                .filter(entry -> entry.getActionType() == AuditActionType.resource_center_update
                        || entry.getActionType() == AuditActionType.resource_center_delete)
                .count();
    }

    private AuditLogEntry latestResourceCenterAudit() {
        List<AuditLogEntry> entries = auditLogRepository.findAll().stream()
                .filter(entry -> entry.getActionType() == AuditActionType.resource_center_update
                        || entry.getActionType() == AuditActionType.resource_center_delete)
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .toList();
        assertThat(entries).isNotEmpty();
        return entries.getFirst();
    }

    private BoundaryCounts boundaryCounts() {
        return new BoundaryCounts(
                configurationComponentRepository.count(),
                configurationRepository.count(),
                scopeDirectoryRepository.count());
    }

    private void createDisabledFixture() throws Exception {
        mockMvc.perform(post(BASE + "/scopes")
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "hidden-scope",
                                  "title": "Hidden Scope",
                                  "layout": "buckets",
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isOk());

        int version = currentVersion();
        mockMvc.perform(post(BASE + "/scopes/hidden-scope/groups")
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "hidden-group",
                                  "title": "Hidden Group",
                                  "type": "bucket",
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isOk());

        version = currentVersion();
        mockMvc.perform(post(BASE + "/scopes/hidden-scope/groups/hidden-group/links")
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Hidden Link",
                                  "url": "https://hidden.example.invalid/page",
                                  "kind": "docs",
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isOk());
    }

    private String findLinkId(String catalogJson, String scopeKey, String groupKey, String title) throws Exception {
        return findLinkNode(catalogJson, scopeKey, groupKey, title).get("id").asText();
    }

    private JsonNode findLinkNode(String catalogJson, String scopeKey, String groupKey, String title) throws Exception {
        JsonNode root = objectMapper.readTree(catalogJson);
        for (JsonNode scope : root.get("scopes")) {
            if (!scopeKey.equals(scope.get("key").asText())) {
                continue;
            }
            for (JsonNode group : scope.get("groups")) {
                if (!groupKey.equals(group.get("key").asText())) {
                    continue;
                }
                for (JsonNode link : group.get("links")) {
                    if (title.equals(link.get("title").asText())) {
                        return link;
                    }
                }
            }
        }
        throw new IllegalStateException("Link not found: " + title);
    }

    private void assertLinkCreateStatus(String url, String kind, int expectedStatus) throws Exception {
        mockMvc.perform(post(BASE + "/scopes/common/groups/platform/links")
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "URL Rule Probe",
                                  "url": "%s",
                                  "kind": "%s"
                                }
                                """.formatted(url, kind)))
                .andExpect(status().is(expectedStatus));
    }

    // ─── contract checklist ──────────────────────────────────────────────────

    @Test
    @DisplayName("#1 GET on empty store seeds once; second GET does not re-seed")
    void get_emptyStoreSeedsOnce() throws Exception {
        assertThat(catalogRepository.count()).isZero();

        mockMvc.perform(get(BASE).with(devHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopes[?(@.key=='sdlc')]").exists())
                .andExpect(jsonPath("$.scopes[?(@.key=='common')]").exists())
                .andExpect(jsonPath("$.scopes[?(@.key=='external')]").exists());

        assertThat(catalogRepository.count()).isOne();
        long versionAfterFirst = catalogRepository.findFirstByOrderByIdAsc().orElseThrow().getVersion();

        mockMvc.perform(get(BASE).with(devHeaders()))
                .andExpect(status().isOk());

        assertThat(catalogRepository.count()).isOne();
        assertThat(catalogRepository.findFirstByOrderByIdAsc().orElseThrow().getVersion())
                .isEqualTo(versionAfterFirst);
    }

    @Test
    @DisplayName("#2 GET as DEVELOPER omits disabled; includeDisabled=true ignored for DEVELOPER")
    void get_developerOmitsDisabled_includeDisabledIgnored() throws Exception {
        readCatalogAsAdmin(false);
        createDisabledFixture();

        mockMvc.perform(get(BASE).with(devHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopes[?(@.key=='hidden-scope')]").doesNotExist());

        mockMvc.perform(get(BASE)
                        .param("includeDisabled", "true")
                        .with(devHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopes[?(@.key=='hidden-scope')]").doesNotExist());
    }

    @Test
    @DisplayName("#3 GET as DEVOPS_ADMIN with includeDisabled=true returns disabled entities")
    void get_adminIncludeDisabled_returnsDisabledEntities() throws Exception {
        readCatalogAsAdmin(false);
        createDisabledFixture();

        mockMvc.perform(get(BASE)
                        .param("includeDisabled", "true")
                        .with(adminHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopes[?(@.key=='hidden-scope')].enabled").value(false))
                .andExpect(jsonPath("$.scopes[?(@.key=='hidden-scope')].groups[?(@.key=='hidden-group')].enabled")
                        .value(false))
                .andExpect(jsonPath(
                                "$.scopes[?(@.key=='hidden-scope')].groups[?(@.key=='hidden-group')].links[?(@.title=='Hidden Link')].enabled")
                        .value(false));
    }

    @Test
    @DisplayName("#4 GET as GUEST returns 200")
    void get_guestReturnsOk() throws Exception {
        mockMvc.perform(get(BASE).with(guestHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopes").isArray());
    }

    @Test
    @DisplayName("#5 all nine mutations return 403 for DEVELOPER and succeed for DEVOPS_ADMIN")
    void mutations_forbiddenForDeveloper_succeedForAdmin() throws Exception {
        readCatalogAsAdmin(false);
        BoundaryCounts before = boundaryCounts();

        int version = currentVersion();
        mockMvc.perform(post(BASE + "/scopes")
                        .with(devHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "mutation-scope",
                                  "title": "Mutation Scope",
                                  "layout": "buckets"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(BASE + "/scopes")
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "mutation-scope",
                                  "title": "Mutation Scope",
                                  "layout": "buckets"
                                }
                                """))
                .andExpect(status().isOk());

        version = currentVersion();
        mockMvc.perform(put(BASE + "/scopes/mutation-scope")
                        .param("expectedVersion", String.valueOf(version))
                        .with(devHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "mutation-scope",
                                  "title": "Mutation Scope Updated",
                                  "layout": "buckets"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(put(BASE + "/scopes/mutation-scope")
                        .param("expectedVersion", String.valueOf(version))
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "mutation-scope",
                                  "title": "Mutation Scope Updated",
                                  "layout": "buckets"
                                }
                                """))
                .andExpect(status().isOk());

        version = currentVersion();
        mockMvc.perform(post(BASE + "/scopes/mutation-scope/groups")
                        .with(devHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "mutation-group",
                                  "title": "Mutation Group",
                                  "type": "bucket"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(BASE + "/scopes/mutation-scope/groups")
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "mutation-group",
                                  "title": "Mutation Group",
                                  "type": "bucket"
                                }
                                """))
                .andExpect(status().isOk());

        version = currentVersion();
        mockMvc.perform(put(BASE + "/scopes/mutation-scope/groups/mutation-group")
                        .param("expectedVersion", String.valueOf(version))
                        .with(devHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "mutation-group",
                                  "title": "Mutation Group Updated",
                                  "type": "bucket"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(put(BASE + "/scopes/mutation-scope/groups/mutation-group")
                        .param("expectedVersion", String.valueOf(version))
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "mutation-group",
                                  "title": "Mutation Group Updated",
                                  "type": "bucket"
                                }
                                """))
                .andExpect(status().isOk());

        version = currentVersion();
        mockMvc.perform(post(BASE + "/scopes/mutation-scope/groups/mutation-group/links")
                        .with(devHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Mutation Link",
                                  "url": "https://mutation.example.invalid/page",
                                  "kind": "docs"
                                }
                                """))
                .andExpect(status().isForbidden());

        MvcResult linkCreate = mockMvc.perform(post(BASE + "/scopes/mutation-scope/groups/mutation-group/links")
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Mutation Link",
                                  "url": "https://mutation.example.invalid/page",
                                  "kind": "docs"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String linkId = findLinkId(
                linkCreate.getResponse().getContentAsString(), "mutation-scope", "mutation-group", "Mutation Link");

        version = currentVersion();
        mockMvc.perform(put(BASE + "/links/" + linkId)
                        .param("expectedVersion", String.valueOf(version))
                        .with(devHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Mutation Link Updated",
                                  "url": "https://mutation.example.invalid/page",
                                  "kind": "docs"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(put(BASE + "/links/" + linkId)
                        .param("expectedVersion", String.valueOf(version))
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Mutation Link Updated",
                                  "url": "https://mutation.example.invalid/page",
                                  "kind": "docs"
                                }
                                """))
                .andExpect(status().isOk());

        version = currentVersion();
        mockMvc.perform(delete(BASE + "/links/" + linkId)
                        .param("expectedVersion", String.valueOf(version))
                        .with(devHeaders()))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete(BASE + "/links/" + linkId)
                        .param("expectedVersion", String.valueOf(version))
                        .with(adminHeaders()))
                .andExpect(status().isOk());

        version = currentVersion();
        mockMvc.perform(delete(BASE + "/scopes/mutation-scope/groups/mutation-group")
                        .param("expectedVersion", String.valueOf(version))
                        .with(devHeaders()))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete(BASE + "/scopes/mutation-scope/groups/mutation-group")
                        .param("expectedVersion", String.valueOf(version))
                        .with(adminHeaders()))
                .andExpect(status().isOk());

        version = currentVersion();
        mockMvc.perform(delete(BASE + "/scopes/mutation-scope")
                        .param("expectedVersion", String.valueOf(version))
                        .with(devHeaders()))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete(BASE + "/scopes/mutation-scope")
                        .param("expectedVersion", String.valueOf(version))
                        .with(adminHeaders()))
                .andExpect(status().isOk());

        assertThat(boundaryCounts()).isEqualTo(before);
    }

    @Test
    @DisplayName("#6 duplicate scope/group keys rejected; same group key in different scopes allowed")
    void keys_duplicateRejected_crossScopeGroupAllowed() throws Exception {
        readCatalogAsAdmin(false);

        mockMvc.perform(post(BASE + "/scopes")
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "security",
                                  "title": "Security",
                                  "layout": "buckets"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post(BASE + "/scopes")
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "security",
                                  "title": "Security Duplicate",
                                  "layout": "buckets"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post(BASE + "/scopes/security/groups")
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "scanners",
                                  "title": "Scanners",
                                  "type": "bucket"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post(BASE + "/scopes/security/groups")
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "scanners",
                                  "title": "Scanners Duplicate",
                                  "type": "bucket"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post(BASE + "/scopes/common/groups")
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "scanners",
                                  "title": "Common Scanners",
                                  "type": "bucket"
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("#7 URL validation rules including .invalid accepted and workspace path rejected for tool")
    void links_urlValidationRules() throws Exception {
        readCatalogAsAdmin(false);

        assertLinkCreateStatus("https://x.example.invalid/", "docs", 200);
        assertLinkCreateStatus("/wwa/deployment-agent", "workspace", 200);
        assertLinkCreateStatus("/wwa/audit-log", "tool", 400);
        assertLinkCreateStatus("javascript:alert(1)", "docs", 400);
        assertLinkCreateStatus("data:text/html,test", "docs", 400);
        assertLinkCreateStatus("vbscript:msgbox(1)", "docs", 400);
        assertLinkCreateStatus("//evil.example.invalid/path", "docs", 400);
        assertLinkCreateStatus("   ", "docs", 400);
        assertLinkCreateStatus("/relative/path", "docs", 400);
        assertLinkCreateStatus("https://github.example.com/wwa/repo", "repo", 200);
    }

    @Test
    @DisplayName("#8 stageKey/stageOrder rules enforced for stage and bucket groups")
    void groups_stageKeyAndStageOrderRules() throws Exception {
        readCatalogAsAdmin(false);

        mockMvc.perform(post(BASE + "/scopes/common/groups")
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "stage-missing-key",
                                  "title": "Stage Missing Key",
                                  "type": "stage",
                                  "stageOrder": 1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post(BASE + "/scopes/common/groups")
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "planning",
                                  "title": "Stage Missing Order",
                                  "type": "stage",
                                  "stageKey": "planning"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post(BASE + "/scopes/common/groups")
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "bucket-with-stage",
                                  "title": "Bucket With Stage",
                                  "type": "bucket",
                                  "stageKey": "planning",
                                  "stageOrder": 1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post(BASE + "/scopes/common/groups")
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "planning",
                                  "title": "Mismatched Stage",
                                  "type": "stage",
                                  "stageKey": "build",
                                  "stageOrder": 4
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("#9 system scope delete rejected; disable and retitle allowed; key change rejected")
    void scopes_systemScopeRules() throws Exception {
        readCatalogAsAdmin(false);
        int version = currentVersion();

        mockMvc.perform(delete(BASE + "/scopes/sdlc")
                        .param("expectedVersion", String.valueOf(version))
                        .with(adminHeaders()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        version = currentVersion();
        mockMvc.perform(put(BASE + "/scopes/sdlc")
                        .param("expectedVersion", String.valueOf(version))
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "sdlc",
                                  "title": "SDLC",
                                  "layout": "stage-strip",
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopes[?(@.key=='sdlc')].enabled").value(false));

        version = currentVersion();
        mockMvc.perform(put(BASE + "/scopes/sdlc")
                        .param("expectedVersion", String.valueOf(version))
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "sdlc",
                                  "title": "SDLC Delivery",
                                  "layout": "stage-strip",
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopes[?(@.key=='sdlc')].title").value("SDLC Delivery"));

        version = currentVersion();
        mockMvc.perform(put(BASE + "/scopes/sdlc")
                        .param("expectedVersion", String.valueOf(version))
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "renamed-sdlc",
                                  "title": "SDLC Delivery",
                                  "layout": "stage-strip",
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("#10 cascade delete writes one audit entry with removed counts")
    void deleteScope_cascadeDeleteAuditsOnce() throws Exception {
        readCatalogAsAdmin(false);

        mockMvc.perform(post(BASE + "/scopes")
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "cascade-scope",
                                  "title": "Cascade Scope",
                                  "layout": "buckets"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post(BASE + "/scopes/cascade-scope/groups")
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "group-a",
                                  "title": "Group A",
                                  "type": "bucket"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post(BASE + "/scopes/cascade-scope/groups")
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "group-b",
                                  "title": "Group B",
                                  "type": "bucket"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post(BASE + "/scopes/cascade-scope/groups/group-a/links")
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Link A1",
                                  "url": "https://a1.example.invalid/",
                                  "kind": "docs"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post(BASE + "/scopes/cascade-scope/groups/group-a/links")
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Link A2",
                                  "url": "https://a2.example.invalid/",
                                  "kind": "docs"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post(BASE + "/scopes/cascade-scope/groups/group-b/links")
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Link B1",
                                  "url": "https://b1.example.invalid/",
                                  "kind": "docs"
                                }
                                """))
                .andExpect(status().isOk());

        long auditsBeforeDelete = resourceCenterAuditCount();
        int version = currentVersion();
        mockMvc.perform(delete(BASE + "/scopes/cascade-scope")
                        .param("expectedVersion", String.valueOf(version))
                        .with(adminHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopes[?(@.key=='cascade-scope')]").doesNotExist());

        assertThat(resourceCenterAuditCount()).isEqualTo(auditsBeforeDelete + 1);
        AuditLogEntry audit = latestResourceCenterAudit();
        assertThat(audit.getActionType()).isEqualTo(AuditActionType.resource_center_delete);
        assertThat(audit.getContextPayload()).containsEntry("entity", "scope");
        assertThat(audit.getContextPayload()).containsEntry("removed_groups", 2);
        assertThat(audit.getContextPayload()).containsEntry("removed_links", 3);
    }

    @Test
    @DisplayName("#11 link move requires both target fields; both supplied moves link and keeps id")
    void updateLink_moveRules() throws Exception {
        readCatalogAsAdmin(false);

        MvcResult create = mockMvc.perform(post(BASE + "/scopes/common/groups/platform/links")
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Movable Link",
                                  "url": "https://move.example.invalid/page",
                                  "kind": "docs"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String linkId = findLinkId(create.getResponse().getContentAsString(), "common", "platform", "Movable Link");

        int version = currentVersion();
        mockMvc.perform(put(BASE + "/links/" + linkId)
                        .param("expectedVersion", String.valueOf(version))
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Movable Link",
                                  "url": "https://move.example.invalid/page",
                                  "kind": "docs",
                                  "targetScopeKey": "common"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        version = currentVersion();
        mockMvc.perform(put(BASE + "/links/" + linkId)
                        .param("expectedVersion", String.valueOf(version))
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Movable Link",
                                  "url": "https://move.example.invalid/page",
                                  "kind": "docs",
                                  "targetGroupKey": "engineering-tools"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        version = currentVersion();
        mockMvc.perform(put(BASE + "/links/" + linkId)
                        .param("expectedVersion", String.valueOf(version))
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Movable Link",
                                  "url": "https://move.example.invalid/page",
                                  "kind": "docs",
                                  "targetScopeKey": "common",
                                  "targetGroupKey": "engineering-tools"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopes[?(@.key=='common')].groups[?(@.key=='platform')].links[?(@.title=='Movable Link')]")
                        .doesNotExist())
                .andExpect(jsonPath(
                                "$.scopes[?(@.key=='common')].groups[?(@.key=='engineering-tools')].links[?(@.id=='%s')]"
                                        .formatted(linkId))
                        .exists());
    }

    @Test
    @DisplayName("#12 stale-write matrix: stale PUT/DELETE 409, current PUT 200, stale POST 200, missing version 400")
    void mutations_staleWriteMatrix() throws Exception {
        readCatalogAsAdmin(false);
        int staleVersion = currentVersion();

        mockMvc.perform(put(BASE + "/scopes/common")
                        .param("expectedVersion", String.valueOf(staleVersion))
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "common",
                                  "title": "Common Updated Once",
                                  "layout": "buckets"
                                }
                                """))
                .andExpect(status().isOk());

        String afterMutation = catalogJson(true);

        mockMvc.perform(put(BASE + "/scopes/common")
                        .param("expectedVersion", String.valueOf(staleVersion))
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "common",
                                  "title": "Common Stale Write",
                                  "layout": "buckets"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPTIMISTIC_LOCK_CONFLICT"));

        assertThat(catalogJson(true)).isEqualTo(afterMutation);

        mockMvc.perform(delete(BASE + "/scopes/common/groups/platform")
                        .param("expectedVersion", String.valueOf(staleVersion))
                        .with(adminHeaders()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPTIMISTIC_LOCK_CONFLICT"));

        assertThat(catalogJson(true)).isEqualTo(afterMutation);

        int currentVersion = currentVersion();
        mockMvc.perform(put(BASE + "/scopes/common")
                        .param("expectedVersion", String.valueOf(currentVersion))
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "common",
                                  "title": "Common Current Write",
                                  "layout": "buckets"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopes[?(@.key=='common')].title").value("Common Current Write"));

        mockMvc.perform(post(BASE + "/scopes")
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "stale-post-scope",
                                  "title": "Stale Post Scope",
                                  "layout": "buckets"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopes[?(@.key=='stale-post-scope')]").exists());

        mockMvc.perform(put(BASE + "/scopes/common")
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "common",
                                  "title": "Missing Version",
                                  "layout": "buckets"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("#13 successful mutation writes one audit; rejected mutation and GET write none")
    void audit_successWritesOne_rejectedAndGetWriteNone() throws Exception {
        readCatalogAsAdmin(false);
        long baseline = resourceCenterAuditCount();

        mockMvc.perform(get(BASE).with(devHeaders()))
                .andExpect(status().isOk());
        assertThat(resourceCenterAuditCount()).isEqualTo(baseline);

        mockMvc.perform(post(BASE + "/scopes")
                        .with(devHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "audit-reject",
                                  "title": "Audit Reject",
                                  "layout": "buckets"
                                }
                                """))
                .andExpect(status().isForbidden());
        assertThat(resourceCenterAuditCount()).isEqualTo(baseline);

        mockMvc.perform(post(BASE + "/scopes")
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "audit-success",
                                  "title": "Audit Success",
                                  "layout": "buckets"
                                }
                                """))
                .andExpect(status().isOk());

        assertThat(resourceCenterAuditCount()).isEqualTo(baseline + 1);
        AuditLogEntry audit = latestResourceCenterAudit();
        assertThat(audit.getActionType()).isEqualTo(AuditActionType.resource_center_update);
        assertThat(audit.getContextPayload()).containsEntry("target_type", "SERVICE_DIRECTORY");
        assertThat(audit.getContextPayload()).containsEntry("entity", "scope");
        assertThat(audit.getContextPayload()).containsEntry("entity_key", "audit-success");
        assertThat(audit.getContextPayload()).containsEntry("operation", "create");
    }

    @Test
    @DisplayName("#14 link move audit includes from/to keys; in-place edit omits them")
    void audit_linkMoveIncludesFromTo_inPlaceEditOmits() throws Exception {
        readCatalogAsAdmin(false);

        MvcResult create = mockMvc.perform(post(BASE + "/scopes/common/groups/platform/links")
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Audit Move Link",
                                  "url": "https://audit-move.example.invalid/page",
                                  "kind": "docs"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String linkId = findLinkId(create.getResponse().getContentAsString(), "common", "platform", "Audit Move Link");

        int version = currentVersion();
        mockMvc.perform(put(BASE + "/links/" + linkId)
                        .param("expectedVersion", String.valueOf(version))
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Audit Move Link",
                                  "url": "https://audit-move.example.invalid/page",
                                  "kind": "docs",
                                  "targetScopeKey": "common",
                                  "targetGroupKey": "engineering-tools"
                                }
                                """))
                .andExpect(status().isOk());

        AuditLogEntry moveAudit = latestResourceCenterAudit();
        assertThat(moveAudit.getActionType()).isEqualTo(AuditActionType.resource_center_update);
        assertThat(moveAudit.getContextPayload()).containsEntry("from_scope_key", "common");
        assertThat(moveAudit.getContextPayload()).containsEntry("from_group_key", "platform");
        assertThat(moveAudit.getContextPayload()).containsEntry("to_scope_key", "common");
        assertThat(moveAudit.getContextPayload()).containsEntry("to_group_key", "engineering-tools");

        version = currentVersion();
        mockMvc.perform(put(BASE + "/links/" + linkId)
                        .param("expectedVersion", String.valueOf(version))
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Audit Move Link Retitled",
                                  "url": "https://audit-move.example.invalid/page",
                                  "kind": "docs"
                                }
                                """))
                .andExpect(status().isOk());

        AuditLogEntry editAudit = latestResourceCenterAudit();
        assertThat(editAudit.getActionType()).isEqualTo(AuditActionType.resource_center_update);
        assertThat(editAudit.getContextPayload()).doesNotContainKey("from_scope_key");
        assertThat(editAudit.getContextPayload()).doesNotContainKey("from_group_key");
        assertThat(editAudit.getContextPayload()).doesNotContainKey("to_scope_key");
        assertThat(editAudit.getContextPayload()).doesNotContainKey("to_group_key");
    }

    @Test
    @DisplayName("#15 second stage-strip scope rejected; second buckets scope allowed")
    void scopes_layoutConstraints() throws Exception {
        readCatalogAsAdmin(false);

        mockMvc.perform(post(BASE + "/scopes")
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "another-strip",
                                  "title": "Another Strip",
                                  "layout": "stage-strip"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post(BASE + "/scopes")
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "another-buckets",
                                  "title": "Another Buckets",
                                  "layout": "buckets"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopes[?(@.key=='another-buckets')]").exists());
    }

    @Test
    @DisplayName("#16 mutations do not write to configuration or scope-directory stores")
    void mutations_doNotTouchBoundaryStores() throws Exception {
        readCatalogAsAdmin(false);
        BoundaryCounts before = boundaryCounts();

        mockMvc.perform(post(BASE + "/scopes")
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "boundary-scope",
                                  "title": "Boundary Scope",
                                  "layout": "buckets"
                                }
                                """))
                .andExpect(status().isOk());

        int version = currentVersion();
        mockMvc.perform(put(BASE + "/scopes/boundary-scope")
                        .param("expectedVersion", String.valueOf(version))
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "boundary-scope",
                                  "title": "Boundary Scope Updated",
                                  "layout": "buckets"
                                }
                                """))
                .andExpect(status().isOk());

        version = currentVersion();
        mockMvc.perform(post(BASE + "/scopes/boundary-scope/groups")
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "boundary-group",
                                  "title": "Boundary Group",
                                  "type": "bucket"
                                }
                                """))
                .andExpect(status().isOk());

        version = currentVersion();
        mockMvc.perform(put(BASE + "/scopes/boundary-scope/groups/boundary-group")
                        .param("expectedVersion", String.valueOf(version))
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "boundary-group",
                                  "title": "Boundary Group Updated",
                                  "type": "bucket"
                                }
                                """))
                .andExpect(status().isOk());

        version = currentVersion();
        MvcResult linkCreate = mockMvc.perform(post(BASE + "/scopes/boundary-scope/groups/boundary-group/links")
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Boundary Link",
                                  "url": "https://boundary.example.invalid/page",
                                  "kind": "docs"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String linkId = findLinkId(
                linkCreate.getResponse().getContentAsString(), "boundary-scope", "boundary-group", "Boundary Link");

        version = currentVersion();
        mockMvc.perform(put(BASE + "/links/" + linkId)
                        .param("expectedVersion", String.valueOf(version))
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Boundary Link Updated",
                                  "url": "https://boundary.example.invalid/page",
                                  "kind": "docs"
                                }
                                """))
                .andExpect(status().isOk());

        version = currentVersion();
        mockMvc.perform(delete(BASE + "/links/" + linkId)
                        .param("expectedVersion", String.valueOf(version))
                        .with(adminHeaders()))
                .andExpect(status().isOk());

        version = currentVersion();
        mockMvc.perform(delete(BASE + "/scopes/boundary-scope/groups/boundary-group")
                        .param("expectedVersion", String.valueOf(version))
                        .with(adminHeaders()))
                .andExpect(status().isOk());

        version = currentVersion();
        mockMvc.perform(delete(BASE + "/scopes/boundary-scope")
                        .param("expectedVersion", String.valueOf(version))
                        .with(adminHeaders()))
                .andExpect(status().isOk());

        assertThat(boundaryCounts()).isEqualTo(before);
    }

    @Test
    @DisplayName("#17 iconKey whitelist (SD-FR-71)")
    void iconKey_whitelist() throws Exception {
        readCatalogAsAdmin(false);

        mockMvc.perform(post(BASE + "/scopes/common/groups/platform/links")
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Icon Key GitHub Link",
                                  "url": "https://github.example.invalid/org/repo",
                                  "kind": "repo",
                                  "iconKey": "github"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopes[?(@.key=='common')].groups[?(@.key=='platform')].links[?(@.title=='Icon Key GitHub Link')].iconKey")
                        .value("github"));

        mockMvc.perform(post(BASE + "/scopes/common/groups/platform/links")
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Icon Key Omitted Link",
                                  "url": "https://omit.example.invalid/page",
                                  "kind": "docs"
                                }
                                """))
                .andExpect(status().isOk());

        String omitJson = catalogJson(true);
        JsonNode omittedLink = findLinkNode(omitJson, "common", "platform", "Icon Key Omitted Link");
        assertThat(omittedLink.hasNonNull("iconKey")).isFalse();

        mockMvc.perform(post(BASE + "/scopes/common/groups/platform/links")
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Icon Key Blank Link",
                                  "url": "https://blank.example.invalid/page",
                                  "kind": "docs",
                                  "iconKey": null
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post(BASE + "/scopes/common/groups/platform/links")
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Icon Key Invalid Link",
                                  "url": "https://invalid.example.invalid/page",
                                  "kind": "docs",
                                  "iconKey": "not-a-real-icon"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post(BASE + "/scopes/common/groups/platform/links")
                        .with(adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Icon Key Url Link",
                                  "url": "https://url-icon.example.invalid/page",
                                  "kind": "docs",
                                  "iconKey": "https://evil.example/x.png"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    private record BoundaryCounts(long components, long configurationItems, long scopeDirectoryEntries) {}
}
