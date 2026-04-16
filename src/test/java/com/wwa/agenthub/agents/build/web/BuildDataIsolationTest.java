package com.wwa.agenthub.agents.build.web;

import com.wwa.agenthub.contracts.AgentId;
import com.wwa.agenthub.contracts.enums.RequestStatus;
import com.wwa.agenthub.contracts.enums.TaskStatus;
import com.wwa.agenthub.domain.releaseflow.ReleaseFlow;
import com.wwa.agenthub.domain.releaseflow.ReleaseFlowService;
import com.wwa.agenthub.domain.releaseflow.Request;
import com.wwa.agenthub.domain.task.Task;
import com.wwa.agenthub.domain.task.TaskRepository;
import com.wwa.agenthub.helper.TestDataHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BA-T22 — Build Agent data isolation tests.
 *
 * <p>Covers the critical scenarios enumerated in design §9 / plan SM-01..SM-13:
 * agent-scoped list visibility, cross-agent 404 probes, stage invariants, and
 * session / legacy-route behavior.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("BA-T22 — Build Agent data isolation")
class BuildDataIsolationTest {

    private static final String BUILD_BASE = "/api/build-agent";
    private static final String DEPLOYMENT_BASE = "/api/deployment-agent";
    private static final String TESTING_BASE = "/api/testing-agent";

    @Autowired private MockMvc mockMvc;
    @Autowired private TestDataHelper helper;
    @Autowired private ReleaseFlowService releaseFlowService;
    @Autowired private TaskRepository taskRepository;

    private ReleaseFlow createFlow(String projectId, String stage, String releaseId) {
        return releaseFlowService.create(projectId, projectId + "-name", releaseId, releaseId, stage);
    }

    // ─── SM-01..SM-03: upload visibility per agent ──────────────────────────

    @Test
    @DisplayName("SM-01: Build upload appears in Build Agent summary")
    void sm01_buildUpload_visibleInBuildSummary() throws Exception {
        ReleaseFlow rf = createFlow("PROJ-BUILD-01", "DEV", "dev-build-0001");
        helper.seedRequest(rf, "DEV", RequestStatus.Pending, AgentId.BUILD_AGENT);

        mockMvc.perform(get(BUILD_BASE + "/release-flows")
                        .header("X-User-Id", "emp-001")
                        .header("X-User-Role", "DEVELOPER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    @DisplayName("SM-02: Build upload is NOT visible in Deployment Agent summary")
    void sm02_buildUpload_notVisibleInDeployment() throws Exception {
        ReleaseFlow rf = createFlow("PROJ-BUILD-02", "DEV", "dev-build-0002");
        helper.seedRequest(rf, "DEV", RequestStatus.Pending, AgentId.BUILD_AGENT);

        mockMvc.perform(get(DEPLOYMENT_BASE + "/release-flows")
                        .header("X-User-Id", "emp-001")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @DisplayName("SM-03: Build upload is NOT visible in Testing Agent summary")
    void sm03_buildUpload_notVisibleInTesting() throws Exception {
        ReleaseFlow rf = createFlow("PROJ-BUILD-03", "DEV", "dev-build-0003");
        helper.seedRequest(rf, "DEV", RequestStatus.Pending, AgentId.BUILD_AGENT);

        mockMvc.perform(get(TESTING_BASE + "/release-flows")
                        .header("X-User-Id", "emp-001")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    // ─── SM-05: cross-agent release with same base id stays separated ────────

    @Test
    @DisplayName("SM-05: Build DEV flow and Deployment SIT flow never stitch")
    void sm05_buildAndDeployment_neverStitch() throws Exception {
        ReleaseFlow buildFlow = createFlow("PROJ-STITCH-A", "DEV", "dev-stitch-0001");
        helper.seedRequest(buildFlow, "DEV", RequestStatus.Pending, AgentId.BUILD_AGENT);

        ReleaseFlow deploymentFlow = createFlow("PROJ-STITCH-A", "SIT", "sit-stitch-0001");
        helper.seedRequest(deploymentFlow, "SIT", RequestStatus.Pending, AgentId.DEPLOYMENT_AGENT);

        mockMvc.perform(get(BUILD_BASE + "/release-flows")
                        .header("X-User-Id", "emp-001")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));

        mockMvc.perform(get(DEPLOYMENT_BASE + "/release-flows")
                        .header("X-User-Id", "emp-001")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    // ─── SM-06/SM-07: cross-agent task probes return 404 ───────────────────

    @Test
    @DisplayName("SM-06: GET /api/build-agent/tasks/{deployment-task-id} returns 404")
    void sm06_crossAgentTaskGet_returns404() throws Exception {
        ReleaseFlow rf = createFlow("PROJ-SM06", "SIT", "sit-sm06-0001");
        Request req = helper.seedRequest(rf, "SIT", RequestStatus.Pending, AgentId.DEPLOYMENT_AGENT);
        Task task = helper.seedTask(req);

        mockMvc.perform(get(BUILD_BASE + "/tasks/" + task.getId())
                        .header("X-User-Id", "emp-001")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("SM-07: POST /api/build-agent/tasks/{testing-task-id}/decision returns 404 and does not mutate state")
    void sm07_crossAgentDecisionPost_returns404() throws Exception {
        ReleaseFlow rf = createFlow("PROJ-SM07", "UAT", "uat-sm07-0001");
        Request req = helper.seedRequest(rf, "UAT", RequestStatus.Pending, AgentId.TESTING_AGENT);
        Task task = helper.seedTask(req, TaskStatus.Awaiting_Review);
        TaskStatus originalStatus = task.getTaskStatus();

        mockMvc.perform(post(BUILD_BASE + "/tasks/" + task.getId() + "/decision")
                        .contentType("application/json")
                        .header("X-User-Id", "emp-003")
                        .header("X-User-Role", "DEVOPS_ADMIN")
                        .content("{\"decision\":\"approve\"}"))
                .andExpect(status().isNotFound());

        // Underlying task is unmodified (guard runs before decision engine).
        Task reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(reloaded.getTaskStatus()).isEqualTo(originalStatus);
    }

    // ─── SM-09: ?linked= is ignored by Build Agent ──────────────────────────

    @Test
    @DisplayName("SM-09: Build Agent getById ignores ?linked= query param")
    void sm09_buildGetById_ignoresLinkedParam() throws Exception {
        ReleaseFlow rf = createFlow("PROJ-SM09", "DEV", "dev-sm09-0001");
        helper.seedRequest(rf, "DEV", RequestStatus.Pending, AgentId.BUILD_AGENT);

        mockMvc.perform(get(BUILD_BASE + "/release-flows/" + rf.getId() + "?linked=abc,def")
                        .header("X-User-Id", "emp-001")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(rf.getId()))
                .andExpect(jsonPath("$.linkedReleaseCount").value(1));
    }

    // ─── SM-12: legacy /api/deployment-agent/auth/login route removed ─────

    @Test
    @DisplayName("SM-12: legacy /api/deployment-agent/auth/login is unavailable")
    void sm12_legacyAuthLoginRoute_unavailable() throws Exception {
        // In the test profile with header-fallback auth and SecurityConfig whitelisting
        // only /api/platform/auth/login, an unauthenticated POST to the legacy route
        // short-circuits to 401 (no handler is reachable without a valid header).
        mockMvc.perform(post("/api/deployment-agent/auth/login")
                        .contentType("application/json")
                        .content("{\"employeeId\":\"emp-001\",\"password\":\"any\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ─── SM-13: Testing Agent cross-agent task probe (v2 R-08) ──────────────

    @Test
    @DisplayName("SM-13: Testing Agent cross-agent task probe returns 404 (v2 R-08 closed)")
    void sm13_testingAgentCrossAgentProbe_returns404() throws Exception {
        ReleaseFlow rf = createFlow("PROJ-SM13", "DEV", "dev-sm13-0001");
        Request req = helper.seedRequest(rf, "DEV", RequestStatus.Pending, AgentId.BUILD_AGENT);
        Task task = helper.seedTask(req);

        mockMvc.perform(get(TESTING_BASE + "/tasks/" + task.getId())
                        .header("X-User-Id", "emp-001")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isNotFound());
    }
}
