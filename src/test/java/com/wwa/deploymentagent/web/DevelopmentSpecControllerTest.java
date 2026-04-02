package com.wwa.deploymentagent.web;

import com.wwa.deploymentagent.contracts.enums.AuditActionType;
import com.wwa.deploymentagent.contracts.enums.DevelopmentSpecStatus;
import com.wwa.deploymentagent.domain.audit.AuditLogEntry;
import com.wwa.deploymentagent.domain.audit.AuditLogRepository;
import com.wwa.deploymentagent.domain.developmentspec.DevelopmentSpec;
import com.wwa.deploymentagent.domain.developmentspec.DevelopmentSpecRepository;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("DevelopmentSpecController")
class DevelopmentSpecControllerTest {

    private static final String BASE = "/api/deployment-agent/development-specs";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DevelopmentSpecRepository developmentSpecRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    @DisplayName("GET /development-specs requires authentication")
    void list_requiresAuthentication() throws Exception {
        mockMvc.perform(get(BASE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /development-specs returns only visible scoped specs")
    void list_returnsVisibleScopedSpecs() throws Exception {
        seedSpec("Visible spec", "AMH HCC", "HTSA-CSI-HCC-AMH-PRJ");
        seedSpec("Hidden spec", "PowerCARD", "HTSA-CSI-CARD-PRD");

        mockMvc.perform(get(BASE)
                        .header("X-User-Id", "emp-dev-001")
                        .header("X-User-Role", "DEVELOPER")
                        .header("X-User-Scopes", "AMH HCC|HTSA-CSI-HCC-AMH-PRJ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("Visible spec"))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    @DisplayName("POST /development-specs creates draft and writes audit")
    void create_createsDraftAndAudits() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody())
                        .header("X-User-Id", "emp-dev-001")
                        .header("X-User-Role", "DEVELOPER")
                        .header("X-User-Scopes", "AMH HCC|HTSA-CSI-HCC-AMH-PRJ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Order sync enhancement"))
                .andExpect(jsonPath("$.programType").value("RPGLE"))
                .andExpect(jsonPath("$.codeStyle").value("FREE_FORMAT"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.generatedContent").doesNotExist())
                .andExpect(jsonPath("$.updatedBy").value("emp-dev-001"));

        AuditLogEntry audit = latestAudit(AuditActionType.development_spec_create);
        assertThat(audit.getContextPayload()).containsEntry("title", "Order sync enhancement");
    }

    @Test
    @DisplayName("POST /development-specs returns 400 when objectives are missing")
    void create_missingObjectives_returnsBadRequest() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Invalid spec",
                                  "moduleName": "ORDSYNC",
                                  "programType": "RPGLE",
                                  "codeStyle": "FREE_FORMAT",
                                  "application": "AMH HCC",
                                  "snowGroup": "HTSA-CSI-HCC-AMH-PRJ",
                                  "sourcePayload": {
                                    "notes": "missing objectives"
                                  }
                                }
                                """)
                        .header("X-User-Id", "emp-dev-001")
                        .header("X-User-Role", "DEVELOPER")
                        .header("X-User-Scopes", "AMH HCC|HTSA-CSI-HCC-AMH-PRJ"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("PUT /development-specs/{id} updates draft fields")
    void update_updatesSpec() throws Exception {
        DevelopmentSpec spec = seedSpec("Old title", "AMH HCC", "HTSA-CSI-HCC-AMH-PRJ");

        mockMvc.perform(put(BASE + "/" + spec.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Updated title",
                                  "moduleName": "ORDSYNC2",
                                  "programType": "SQLRPGLE",
                                  "codeStyle": "BOTH",
                                  "application": "AMH HCC",
                                  "snowGroup": "HTSA-CSI-HCC-AMH-PRJ",
                                  "sourcePayload": {
                                    "businessObjective": "Reduce duplicate syncs",
                                    "implementationObjective": ["Add idempotency guard"]
                                  },
                                  "version": %d
                                }
                                """.formatted(spec.getVersion()))
                        .header("X-User-Id", "emp-dev-001")
                        .header("X-User-Role", "DEVELOPER")
                        .header("X-User-Scopes", "AMH HCC|HTSA-CSI-HCC-AMH-PRJ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated title"))
                .andExpect(jsonPath("$.moduleName").value("ORDSYNC2"))
                .andExpect(jsonPath("$.programType").value("SQLRPGLE"))
                .andExpect(jsonPath("$.codeStyle").value("BOTH"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    @DisplayName("POST /development-specs/{id}/generate creates generated content")
    void generate_returnsGeneratedSpec() throws Exception {
        DevelopmentSpec spec = seedSpec("Generate me", "AMH HCC", "HTSA-CSI-HCC-AMH-PRJ");

        mockMvc.perform(post(BASE + "/" + spec.getId() + "/generate")
                        .header("X-User-Id", "emp-dev-001")
                        .header("X-User-Role", "DEVELOPER")
                        .header("X-User-Scopes", "AMH HCC|HTSA-CSI-HCC-AMH-PRJ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("GENERATED"))
                .andExpect(jsonPath("$.generatedBy").value("emp-dev-001"))
                .andExpect(jsonPath("$.generatedContent").value(containsString("## Title")))
                .andExpect(jsonPath("$.generatedPayload.title").value("Generate me"));

        AuditLogEntry audit = latestAudit(AuditActionType.development_spec_generate);
        assertThat(audit.getContextPayload()).containsEntry("developmentSpecId", spec.getId());
    }

    @Test
    @DisplayName("GET /development-specs/{id}/export returns markdown download")
    void export_returnsMarkdownDownload() throws Exception {
        DevelopmentSpec spec = generatedSpec("Export me");

        mockMvc.perform(get(BASE + "/" + spec.getId() + "/export")
                        .header("X-User-Id", "emp-dev-001")
                        .header("X-User-Role", "DEVELOPER")
                        .header("X-User-Scopes", "AMH HCC|HTSA-CSI-HCC-AMH-PRJ"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"export-me.md\""))
                .andExpect(content().contentType("text/markdown"))
                .andExpect(content().string(containsString("## Title")));

        AuditLogEntry audit = latestAudit(AuditActionType.development_spec_export);
        assertThat(audit.getContextPayload())
                .containsEntry("developmentSpecId", spec.getId())
                .containsEntry("format", "markdown");
    }

    @Test
    @DisplayName("GET /development-specs/{id}/export returns json download")
    void export_returnsJsonDownload() throws Exception {
        DevelopmentSpec spec = generatedSpec("Export json");

        mockMvc.perform(get(BASE + "/" + spec.getId() + "/export")
                        .queryParam("format", "json")
                        .header("X-User-Id", "emp-dev-001")
                        .header("X-User-Role", "DEVELOPER")
                        .header("X-User-Scopes", "AMH HCC|HTSA-CSI-HCC-AMH-PRJ"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"export-json.json\""))
                .andExpect(content().contentType("application/json"))
                .andExpect(content().string(containsString("\"title\" : \"Export json\"")));
    }

    @Test
    @DisplayName("GET /development-specs/{id}/export rejects draft spec")
    void export_draft_returnsBadRequest() throws Exception {
        DevelopmentSpec spec = seedSpec("Draft export", "AMH HCC", "HTSA-CSI-HCC-AMH-PRJ");

        mockMvc.perform(get(BASE + "/" + spec.getId() + "/export")
                        .header("X-User-Id", "emp-dev-001")
                        .header("X-User-Role", "DEVELOPER")
                        .header("X-User-Scopes", "AMH HCC|HTSA-CSI-HCC-AMH-PRJ"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("GET /development-specs/{id}/export returns 403 outside scope")
    void export_outOfScope_returnsForbidden() throws Exception {
        DevelopmentSpec spec = generatedSpec("Scoped export");

        mockMvc.perform(get(BASE + "/" + spec.getId() + "/export")
                        .header("X-User-Id", "emp-dev-002")
                        .header("X-User-Role", "DEVELOPER")
                        .header("X-User-Scopes", "PowerCARD|HTSA-CSI-CARD-PRD"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("POST /development-specs/{id}/generate returns 403 outside scope")
    void generate_outOfScope_returnsForbidden() throws Exception {
        DevelopmentSpec spec = seedSpec("Scoped spec", "AMH HCC", "HTSA-CSI-HCC-AMH-PRJ");

        mockMvc.perform(post(BASE + "/" + spec.getId() + "/generate")
                        .header("X-User-Id", "emp-dev-002")
                        .header("X-User-Role", "DEVELOPER")
                        .header("X-User-Scopes", "PowerCARD|HTSA-CSI-CARD-PRD"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private DevelopmentSpec generatedSpec(String title) {
        DevelopmentSpec spec = seedSpec(title, "AMH HCC", "HTSA-CSI-HCC-AMH-PRJ");
        spec.setGeneratedPayload(new LinkedHashMap<>(Map.of(
                "title", title,
                "scope", "Application: AMH HCC\nSNOW Group: HTSA-CSI-HCC-AMH-PRJ"
        )));
        spec.setGeneratedContent("## Title\n\n" + title + "\n\n## Scope\n\nApplication: AMH HCC\nSNOW Group: HTSA-CSI-HCC-AMH-PRJ");
        spec.setGeneratedBy("emp-dev-001");
        spec.setStatus(DevelopmentSpecStatus.GENERATED);
        return developmentSpecRepository.saveAndFlush(spec);
    }

    private DevelopmentSpec seedSpec(String title, String application, String snowGroup) {
        DevelopmentSpec spec = new DevelopmentSpec();
        spec.setTitle(title);
        spec.setModuleName("ORDSYNC");
        spec.setProgramType("RPGLE");
        spec.setCodeStyle("FREE_FORMAT");
        spec.setApplication(application);
        spec.setSnowGroup(snowGroup);
        spec.setSourcePayload(new LinkedHashMap<>(Map.of(
                "businessObjective", "Reduce manual follow-up",
                "implementationObjective", List.of("Add validation", "Generate output")
        )));
        spec.setStatus(DevelopmentSpecStatus.DRAFT);
        spec.setCreatedBy("seed");
        spec.setUpdatedBy("seed");
        return developmentSpecRepository.saveAndFlush(spec);
    }

    private String validRequestBody() {
        return """
                {
                  "title": "Order sync enhancement",
                  "moduleName": "ORDSYNC",
                  "programType": "RPGLE",
                  "codeStyle": "FREE_FORMAT",
                  "application": "AMH HCC",
                  "snowGroup": "HTSA-CSI-HCC-AMH-PRJ",
                  "sourcePayload": {
                    "businessObjective": "Reduce manual order follow-up",
                    "implementationObjective": ["Add validation", "Generate result details"],
                    "inputs": ["orderId"],
                    "outputs": ["resultFlag"]
                  }
                }
                """;
    }

    private AuditLogEntry latestAudit(AuditActionType actionType) {
        return auditLogRepository.findByActionType(
                        actionType,
                        PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "timestamp")))
                .getContent()
                .getFirst();
    }
}
