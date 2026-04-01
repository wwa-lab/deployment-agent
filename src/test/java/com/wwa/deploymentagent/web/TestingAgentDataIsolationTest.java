package com.wwa.deploymentagent.web;

import com.wwa.deploymentagent.contracts.AgentId;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlow;
import com.wwa.deploymentagent.helper.TestDataHelper;
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
@DisplayName("Testing Agent data isolation")
class TestingAgentDataIsolationTest {

    private static final String TA_BASE = "/api/testing-agent/release-flows";
    private static final String DA_BASE = "/api/deployment-agent/release-flows";

    @Autowired private MockMvc mockMvc;
    @Autowired private TestDataHelper helper;

    @Test
    @DisplayName("testing-agent list does not show deployment-agent flows")
    void testingAgent_doesNotSeeDeploymentAgentFlows() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        helper.seedRequest(rf, AgentId.DEPLOYMENT_AGENT);

        mockMvc.perform(get(TA_BASE)
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @DisplayName("deployment-agent list shows all flows regardless of agent")
    void deploymentAgent_showsAllFlows() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        helper.seedRequest(rf, AgentId.TESTING_AGENT);

        mockMvc.perform(get(DA_BASE)
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    @DisplayName("each agent sees only its own flows when same project uploaded via both")
    void sameProject_eachAgentSeesOwnFlows() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        helper.seedRequest(rf, AgentId.DEPLOYMENT_AGENT);
        helper.seedRequest(rf, AgentId.TESTING_AGENT);

        // Testing agent sees only 1 flow (with testing-agent request)
        mockMvc.perform(get(TA_BASE)
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));

        // Deployment agent also sees 1 flow (with both requests aggregated)
        mockMvc.perform(get(DA_BASE)
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    @DisplayName("null-agent legacy data is not visible in testing-agent list")
    void nullAgent_notVisibleInTestingAgent() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        helper.seedRequest(rf); // no agent = null

        mockMvc.perform(get(TA_BASE)
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }
}
