package com.wwa.deploymentagent.web;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("T13.3 - ConfigurationController API contract")
class ConfigurationControllerTest {

    private static final String BASE = "/api/deployment-agent/config";

    @Autowired
    private MockMvc mockMvc;

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
}
