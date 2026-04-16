package com.wwa.agenthub.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("T13.3 - ConfigurationController API contract")
class ConfigurationControllerTest {

    private static final String BASE = "/api/platform/config";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ─── listAll ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listAll_returnsOk - GET /config with any auth returns 200")
    void listAll_returnsOk() throws Exception {
        mockMvc.perform(get(BASE)
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "DEVELOPER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ─── upsert ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("upsert_devopsAdmin_succeeds - POST /config with DEVOPS_ADMIN and valid body returns 200 with config item")
    void upsert_devopsAdmin_succeeds() throws Exception {
        mockMvc.perform(post(BASE)
                        .header("X-User-Id", "admin-user")
                        .header("X-User-Role", "DEVOPS_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\": \"jenkins_url\", \"value\": \"http://jenkins.example.com\", \"description\": \"Jenkins base URL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configKey").value("jenkins_url"))
                .andExpect(jsonPath("$.configValue").value("http://jenkins.example.com"));
    }

    @Test
    @DisplayName("upsert_nonAdmin_returns403 - POST /config with DEVELOPER returns 403")
    void upsert_nonAdmin_returns403() throws Exception {
        mockMvc.perform(post(BASE)
                        .header("X-User-Id", "dev-user")
                        .header("X-User-Role", "DEVELOPER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\": \"jenkins_url\", \"value\": \"http://jenkins.example.com\", \"description\": \"test\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("listAll masks sensitive values in raw configuration responses")
    void listAll_masksSensitiveValues() throws Exception {
        mockMvc.perform(post(BASE)
                        .header("X-User-Id", "admin-user")
                        .header("X-User-Role", "DEVOPS_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"componentId\":\"jenkins\",\"key\":\"jenkins_api_token\",\"value\":\"super-secret\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get(BASE)
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "DEVELOPER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.configKey=='jenkins_api_token')].configValue").value(hasItem("••••••••")));
    }

    @Test
    @DisplayName("deleteComponent_devopsAdmin_succeeds - DELETE /config/components/{id} returns 204")
    void deleteComponent_devopsAdmin_succeeds() throws Exception {
        String createResponse = mockMvc.perform(post(BASE + "/components")
                        .header("X-User-Id", "admin-user")
                        .header("X-User-Role", "DEVOPS_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "componentId": "jenkins",
                                  "displayName": "Scoped Jenkins",
                                  "area": "CI/CD",
                                  "application": "AMH HCC",
                                  "serviceEndpoint": "http://jenkins.example.com",
                                  "serviceUser": "svc-user",
                                  "credentialValue": "svc-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String componentInstanceId = objectMapper.readTree(createResponse)
                .path("componentInstanceId")
                .asText();

        mockMvc.perform(delete(BASE + "/components/{componentInstanceId}", componentInstanceId)
                        .header("X-User-Id", "admin-user")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE + "/components")
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "DEVELOPER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].componentInstanceId", not(hasItem(componentInstanceId))));
    }

    @Test
    @DisplayName("deleteComponent_nonAdmin_returns403 - DELETE /config/components/{id} with DEVELOPER returns 403")
    void deleteComponent_nonAdmin_returns403() throws Exception {
        mockMvc.perform(delete(BASE + "/components/{componentInstanceId}", "missing-id")
                        .header("X-User-Id", "dev-user")
                        .header("X-User-Role", "DEVELOPER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("scope directory can be created, listed, and deleted by DEVOPS_ADMIN")
    void scopeDirectory_crudFlow() throws Exception {
        String createResponse = mockMvc.perform(post(BASE + "/scopes")
                        .header("X-User-Id", "admin-user")
                        .header("X-User-Role", "DEVOPS_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "application": "AMH HCC",
                                  "snowGroup": "HTSA-CSI-HCC-AMH-PRJ"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application").value("AMH HCC"))
                .andExpect(jsonPath("$.snowGroup").value("HTSA-CSI-HCC-AMH-PRJ"))
                .andExpect(jsonPath("$.scopeSource").value("SNOW Group Default"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String scopeId = objectMapper.readTree(createResponse).path("id").asText();

        mockMvc.perform(get(BASE + "/scopes")
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "DEVELOPER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(scopeId))
                .andExpect(jsonPath("$[0].application").value("AMH HCC"));

        mockMvc.perform(delete(BASE + "/scopes/{id}", scopeId)
                        .header("X-User-Id", "admin-user")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE + "/scopes")
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "DEVELOPER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("scope directory supports agent-specific overrides")
    void scopeDirectory_agentOverrideCrudFlow() throws Exception {
        String createResponse = mockMvc.perform(post(BASE + "/scopes")
                        .header("X-User-Id", "admin-user")
                        .header("X-User-Role", "DEVOPS_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "application": "AMH HCC",
                                  "snowGroup": "HTSA-CSI-HCC-AMH-PRJ",
                                  "agent": "deployment-agent"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application").value("AMH HCC"))
                .andExpect(jsonPath("$.snowGroup").value("HTSA-CSI-HCC-AMH-PRJ"))
                .andExpect(jsonPath("$.agent").value("deployment-agent"))
                .andExpect(jsonPath("$.scopeSource").value("Agent Override"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String scopeId = objectMapper.readTree(createResponse).path("id").asText();

        mockMvc.perform(get(BASE + "/scopes")
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "DEVELOPER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(scopeId))
                .andExpect(jsonPath("$[0].agent").value("deployment-agent"))
                .andExpect(jsonPath("$[0].scopeSource").value("Agent Override"));
    }

    @Test
    @DisplayName("scope directory rejects agent overrides without snow group")
    void scopeDirectory_agentOverrideRequiresSnowGroup() throws Exception {
        mockMvc.perform(post(BASE + "/scopes")
                        .header("X-User-Id", "admin-user")
                        .header("X-User-Role", "DEVOPS_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "application": "AMH HCC",
                                  "agent": "deployment-agent"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Agent scope requires both Application and SNOW Group to be set"));
    }

    @Test
    @DisplayName("scope directory rejects non-admin writes")
    void scopeDirectory_nonAdminReturnsForbidden() throws Exception {
        mockMvc.perform(post(BASE + "/scopes")
                        .header("X-User-Id", "dev-user")
                        .header("X-User-Role", "DEVELOPER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "application": "AMH HCC"
                                }
                                """))
                .andExpect(status().isForbidden());
    }
}
