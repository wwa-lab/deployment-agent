package com.wwa.deploymentagent.web;

import com.wwa.deploymentagent.contracts.enums.RequestStatus;
import org.springframework.http.MediaType;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlow;
import com.wwa.deploymentagent.domain.releaseflow.Request;
import com.wwa.deploymentagent.domain.task.Task;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("T13.3 - ReleaseFlowController API contract")
class ReleaseFlowControllerTest {

    private static final String BASE = "/api/deployment-agent/release-flows";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestDataHelper helper;

    // ─── list ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("list_returnsEmptyPage_whenNoData - GET /release-flows returns 200 with empty content array")
    void list_returnsEmptyPage_whenNoData() throws Exception {
        mockMvc.perform(get(BASE)
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.total").isNumber());
    }

    @Test
    @DisplayName("list_returnsFlows_withSeedData - seeded release flow appears in content")
    void list_returnsFlows_withSeedData() throws Exception {
        helper.seedReleaseFlow();

        mockMvc.perform(get(BASE)
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    @DisplayName("list_returnsStageStatuses_forAllEnvironments - GET /release-flows includes SIT/UAT/PROD request statuses")
    void list_returnsStageStatuses_forAllEnvironments() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        helper.seedRequest(rf, com.wwa.deploymentagent.contracts.enums.Stage.SIT, RequestStatus.Completed);
        helper.seedRequest(rf, com.wwa.deploymentagent.contracts.enums.Stage.UAT, RequestStatus.Running);

        mockMvc.perform(get(BASE)
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sitStatus").value("Completed"))
                .andExpect(jsonPath("$.data[0].uatStatus").value("Running"))
                .andExpect(jsonPath("$.data[0].prodStatus").value("Pending"));
    }

    // ─── getById ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getById_returnsDetail - GET /release-flows/{id} returns 200 with id and projectId")
    void getById_returnsDetail() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf);
        Task task = helper.seedTask(req);

        mockMvc.perform(get(BASE + "/" + rf.getId())
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(rf.getId()))
                .andExpect(jsonPath("$.projectId").value(rf.getProjectId()))
                .andExpect(jsonPath("$.requests[0].releaseFlowId").value(rf.getId()));
    }

    @Test
    @DisplayName("updateRequestRundown_returnsUpdatedFields - PATCH /release-flows/{flowId}/requests/{id}/rundown returns updated request")
    void updateRequestRundown_returnsUpdatedFields() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf);
        helper.seedTask(req);

        mockMvc.perform(patch(BASE + "/" + rf.getId() + "/requests/" + req.getId() + "/rundown")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "snowGroup": "HTSA-CSI-HCC-AMH-PRJ",
                                  "application": "AMH HCC",
                                  "site": "HK",
                                  "estimatedRemainingMinutes": 120
                                }
                                """)
                        .header("X-User-Id", "emp-001")
                        .header("X-User-Role", "DEVELOPER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(req.getId()))
                .andExpect(jsonPath("$.snowGroup").value("HTSA-CSI-HCC-AMH-PRJ"))
                .andExpect(jsonPath("$.application").value("AMH HCC"))
                .andExpect(jsonPath("$.site").value("HK"))
                .andExpect(jsonPath("$.estimatedRemainingMinutes").value(120));
    }

    @Test
    @DisplayName("startRequestDeployment_movesFirstTaskToReady - POST /release-flows/{flowId}/requests/{id}/start returns running request")
    void startRequestDeployment_movesFirstTaskToReady() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf);
        helper.seedTask(req);

        mockMvc.perform(post(BASE + "/" + rf.getId() + "/requests/" + req.getId() + "/start")
                        .header("X-User-Id", "emp-001")
                        .header("X-User-Role", "DEVELOPER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestStatus").value("Running"))
                .andExpect(jsonPath("$.tasks[0].taskStatus").value("Ready_For_Execution"));
    }

    @Test
    @DisplayName("markRequestFailed_marksActiveTasksFailed - POST /release-flows/{flowId}/requests/{id}/fail returns failed request")
    void markRequestFailed_marksActiveTasksFailed() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf);
        helper.seedTask(req);

        mockMvc.perform(post(BASE + "/" + rf.getId() + "/requests/" + req.getId() + "/fail")
                        .header("X-User-Id", "emp-001")
                        .header("X-User-Role", "DEVELOPER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestStatus").value("Failed"))
                .andExpect(jsonPath("$.tasks[0].taskStatus").value("Failed"));
    }

    @Test
    @DisplayName("getById_unknownId_returns404 - GET /release-flows/unknown-xyz returns 404")
    void getById_unknownId_returns404() throws Exception {
        mockMvc.perform(get(BASE + "/unknown-xyz")
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isNotFound());
    }

    // ─── pagination validation ────────────────────────────────────────────────

    @Test
    @DisplayName("list_invalidPageParam_returns400 - page=-1 returns 400")
    void list_invalidPageParam_returns400() throws Exception {
        mockMvc.perform(get(BASE)
                        .param("page", "-1")
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("list_invalidSizeParam_returns400 - size=0 returns 400")
    void list_invalidSizeParam_returns400() throws Exception {
        mockMvc.perform(get(BASE)
                        .param("size", "0")
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("list_sizeTooLarge_returns400 - size=101 returns 400")
    void list_sizeTooLarge_returns400() throws Exception {
        mockMvc.perform(get(BASE)
                        .param("size", "101")
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isBadRequest());
    }
}
