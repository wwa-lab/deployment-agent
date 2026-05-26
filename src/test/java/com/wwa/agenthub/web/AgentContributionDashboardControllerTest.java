package com.wwa.agenthub.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("AgentContributionDashboardController API contract")
class AgentContributionDashboardControllerTest {

    private static final String BASE = "/api/platform/agent-contribute-dashboard/statuses";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("getStatuses_returnsDefaultEmptyOverrides")
    void getStatuses_returnsDefaultEmptyOverrides() throws Exception {
        mockMvc.perform(get(BASE)
                        .header("X-User-Id", "dev-user")
                        .header("X-User-Role", "DEVELOPER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statuses").isMap())
                .andExpect(jsonPath("$.updatedBy").doesNotExist());
    }

    @Test
    @DisplayName("updateStatuses_devopsAdminPersistsStatusOverrides")
    void updateStatuses_devopsAdminPersistsStatusOverrides() throws Exception {
        mockMvc.perform(put(BASE)
                        .header("X-User-Id", "admin-user")
                        .header("X-User-Role", "DEVOPS_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "statuses": {
                                    "planning": "implemented",
                                    "testing": "in-progress",
                                    "discovery": "backlog",
                                    "maintenance": "not-implemented"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statuses.testing").value("in-progress"))
                .andExpect(jsonPath("$.statuses.discovery").value("backlog"))
                .andExpect(jsonPath("$.updatedBy").value("admin-user"));

        mockMvc.perform(get(BASE)
                        .header("X-User-Id", "dev-user")
                        .header("X-User-Role", "DEVELOPER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statuses.testing").value("in-progress"))
                .andExpect(jsonPath("$.statuses.maintenance").value("not-implemented"));
    }

    @Test
    @DisplayName("updateStatuses_nonAdminReturnsForbidden")
    void updateStatuses_nonAdminReturnsForbidden() throws Exception {
        mockMvc.perform(put(BASE)
                        .header("X-User-Id", "dev-user")
                        .header("X-User-Role", "DEVELOPER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "statuses": {
                                    "testing": "implemented"
                                  }
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("updateStatuses_rejectsInvalidStatus")
    void updateStatuses_rejectsInvalidStatus() throws Exception {
        mockMvc.perform(put(BASE)
                        .header("X-User-Id", "admin-user")
                        .header("X-User-Role", "DEVOPS_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "statuses": {
                                    "testing": "done"
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("updateStatuses_rejectsUnknownStageKey")
    void updateStatuses_rejectsUnknownStageKey() throws Exception {
        mockMvc.perform(put(BASE)
                        .header("X-User-Id", "admin-user")
                        .header("X-User-Role", "DEVOPS_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "statuses": {
                                    "invalid-stage": "implemented"
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
