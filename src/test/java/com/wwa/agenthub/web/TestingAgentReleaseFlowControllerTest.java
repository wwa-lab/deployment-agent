package com.wwa.agenthub.web;

import com.wwa.agenthub.contracts.AgentId;
import com.wwa.agenthub.domain.releaseflow.ReleaseFlow;
import com.wwa.agenthub.helper.TestDataHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("TestingAgentReleaseFlowController API contract")
class TestingAgentReleaseFlowControllerTest {

    private static final String BASE = "/api/testing-agent/release-flows";

    @Autowired private MockMvc mockMvc;
    @Autowired private TestDataHelper helper;

    @Test
    @DisplayName("list returns empty page when no data")
    void list_returnsEmptyPage_whenNoData() throws Exception {
        mockMvc.perform(get(BASE)
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.total").isNumber());
    }

    @Test
    @DisplayName("list returns only testing-agent flows")
    void list_returnsOnlyTestingAgentFlows() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        helper.seedRequestWithAgent(rf, AgentId.TESTING_AGENT);

        mockMvc.perform(get(BASE)
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    @DisplayName("list does not return deployment-agent flows")
    void list_excludesDeploymentAgentFlows() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        helper.seedRequestWithAgent(rf, AgentId.DEPLOYMENT_AGENT);

        mockMvc.perform(get(BASE)
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @DisplayName("list overrides client-supplied agent param to testing-agent")
    void list_overridesAgentParam() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        helper.seedRequestWithAgent(rf, AgentId.TESTING_AGENT);

        mockMvc.perform(get(BASE)
                        .param("agent", AgentId.DEPLOYMENT_AGENT)
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    @DisplayName("list does not return null-agent legacy data")
    void list_excludesNullAgentData() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        helper.seedRequest(rf); // no agent

        mockMvc.perform(get(BASE)
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @DisplayName("getById returns 200 for existing flow")
    void getById_returns200_forExistingFlow() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        helper.seedRequestWithAgent(rf, AgentId.TESTING_AGENT);

        mockMvc.perform(get(BASE + "/" + rf.getId())
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(rf.getId()));
    }
}
