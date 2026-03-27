package com.wwa.deploymentagent.web;

import com.wwa.deploymentagent.contracts.enums.RequestStatus;
import com.wwa.deploymentagent.contracts.enums.Stage;
import org.springframework.http.MediaType;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlow;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlowService;
import com.wwa.deploymentagent.domain.releaseflow.Request;
import com.wwa.deploymentagent.domain.releaseflow.RequestRepository;
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

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

    @Autowired
    private RequestRepository requestRepository;

    @Autowired
    private ReleaseFlowService releaseFlowService;

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
        ReleaseFlow releaseFlow = helper.seedReleaseFlow();
        helper.seedRequest(releaseFlow);

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

    @Test
    @DisplayName("list_viewStitched_attemptViewSwitchesBetweenLatestAndHistoryForRepeatedStageAttempts")
    void list_viewStitched_attemptViewSwitchesBetweenLatestAndHistoryForRepeatedStageAttempts() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        helper.seedRequest(rf, Stage.SIT, RequestStatus.Failed);
        helper.seedRequest(rf, Stage.SIT, RequestStatus.Completed);

        mockMvc.perform(get(BASE)
                        .param("view", "stitched")
                        .param("attemptView", "latest")
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sitStatus").value("Completed"));

        mockMvc.perform(get(BASE)
                        .param("view", "stitched")
                        .param("attemptView", "history")
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sitStatus").value("Failed"));
    }

    @Test
    @DisplayName("list_filtersByScopeFields_andReturnsScopeSummary")
    void list_filtersByScopeFields_andReturnsScopeSummary() throws Exception {
        ReleaseFlow releaseFlow = helper.seedReleaseFlow();
        Request matchingRequest = helper.seedRequest(releaseFlow);
        matchingRequest.setApplication("AMH HCC");
        matchingRequest.setSnowGroup("HTSA-CSI-HCC-AMH-PRJ");
        matchingRequest.setAgent("Deployment Agent");
        matchingRequest.setOwner("alice");
        requestRepository.save(matchingRequest);

        ReleaseFlow otherFlow = releaseFlowService.create(
                "PROJ-002", "PowerCARD", "sit-powercard-0001", "sit-powercard-0001", Stage.SIT);
        Request otherRequest = helper.seedRequest(otherFlow);
        otherRequest.setApplication("PowerCARD");
        otherRequest.setSnowGroup("HTSA-CSI-CARD-PRD");
        otherRequest.setAgent("PowerCARD Agent");
        requestRepository.save(otherRequest);

        mockMvc.perform(get(BASE)
                        .param("application", "AMH")
                        .param("snowGroup", "HTSA-CSI-HCC")
                        .param("agent", "Deployment")
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].application").value("AMH HCC"))
                .andExpect(jsonPath("$.data[0].snowGroup").value("HTSA-CSI-HCC-AMH-PRJ"))
                .andExpect(jsonPath("$.data[0].agent").value("Deployment Agent"))
                .andExpect(jsonPath("$.data[0].owner").value("alice"));
    }

    @Test
    @DisplayName("list_viewStitched_groupsStagePrefixedReleaseFamilyIntoOneSummary")
    void list_viewStitched_groupsStagePrefixedReleaseFamilyIntoOneSummary() throws Exception {
        ReleaseFlow sitFlow = releaseFlowService.create("PROJ-STITCH", "Stitched Project", "sit-01", "sit-01", Stage.SIT);
        Request sitRequest = helper.seedRequest(sitFlow, Stage.SIT, RequestStatus.Completed);
        sitRequest.setApplication("AMH HCC");
        sitRequest.setOwner("alice");
        requestRepository.save(sitRequest);

        ReleaseFlow uatFlow = releaseFlowService.create("PROJ-STITCH", "Stitched Project", "uat-01", "uat-01", Stage.UAT);
        Request uatRequest = helper.seedRequest(uatFlow, Stage.UAT, RequestStatus.Pending);
        uatRequest.setApplication("AMH HCC");
        uatRequest.setOwner("alice");
        requestRepository.save(uatRequest);

        mockMvc.perform(get(BASE)
                        .param("view", "stitched")
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].stitched").value(true))
                .andExpect(jsonPath("$.data[0].linkedReleaseCount").value(2))
                .andExpect(jsonPath("$.data[0].currentStage").value("UAT"))
                .andExpect(jsonPath("$.data[0].sitPresent").value(true))
                .andExpect(jsonPath("$.data[0].uatPresent").value(true))
                .andExpect(jsonPath("$.data[0].prodPresent").value(false));
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
    @DisplayName("getById_returnsAttemptNumbers_forRepeatedStageRequests")
    void getById_returnsAttemptNumbers_forRepeatedStageRequests() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request first = helper.seedRequest(rf, Stage.SIT, RequestStatus.Failed);
        Request second = helper.seedRequest(rf, Stage.SIT, RequestStatus.Pending);
        helper.seedTask(first);
        helper.seedTask(second);

        mockMvc.perform(get(BASE + "/" + rf.getId())
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requests.length()").value(2))
                .andExpect(jsonPath("$.requests[*].attemptNumber", containsInAnyOrder(2, 1)));
    }

    @Test
    @DisplayName("getById_withLinkedReleaseIds_returnsStitchedDetail")
    void getById_withLinkedReleaseIds_returnsStitchedDetail() throws Exception {
        ReleaseFlow sitFlow = releaseFlowService.create("PROJ-LINK", "Linked Project", "sit-01", "sit-01", Stage.SIT);
        Request sitRequest = helper.seedRequest(sitFlow, Stage.SIT, RequestStatus.Pending);
        helper.seedTask(sitRequest);

        ReleaseFlow prodFlow = releaseFlowService.create("PROJ-LINK", "Linked Project", "prod-01", "prod-01", Stage.PROD);
        Request prodRequest = helper.seedRequest(prodFlow, Stage.PROD, RequestStatus.Pending);
        helper.seedTask(prodRequest);

        mockMvc.perform(get(BASE + "/" + prodFlow.getId())
                        .param("linked", sitFlow.getId())
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stitched").value(true))
                .andExpect(jsonPath("$.linkedReleaseCount").value(2))
                .andExpect(jsonPath("$.currentStage").value("PROD"))
                .andExpect(jsonPath("$.requests.length()").value(2));
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
                                  "agent": "Deployment Agent",
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
                .andExpect(jsonPath("$.agent").value("Deployment Agent"))
                .andExpect(jsonPath("$.site").value("HK"))
                .andExpect(jsonPath("$.estimatedRemainingMinutes").value(120));
    }

    @Test
    @DisplayName("updateRequestRundown_allowsDevOpsAdminToChangeOwner")
    void updateRequestRundown_allowsAdminToChangeOwner() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf);

        mockMvc.perform(patch(BASE + "/" + rf.getId() + "/requests/" + req.getId() + "/rundown")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "owner": "alice"
                                }
                                """)
                        .header("X-User-Id", "emp-003")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner").value("alice"));
    }

    @Test
    @DisplayName("startRequestDeployment_movesFirstTaskToReady - POST /release-flows/{flowId}/requests/{id}/start returns running request")
    void startRequestDeployment_movesFirstTaskToReady() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf);
        req.setOwner("emp-001");
        requestRepository.save(req);
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
        req.setOwner("emp-001");
        requestRepository.save(req);
        helper.seedTask(req);

        mockMvc.perform(post(BASE + "/" + rf.getId() + "/requests/" + req.getId() + "/fail")
                        .header("X-User-Id", "emp-001")
                        .header("X-User-Role", "DEVELOPER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestStatus").value("Failed"))
                .andExpect(jsonPath("$.tasks[0].taskStatus").value("Failed"));
    }

    @Test
    @DisplayName("startRequestDeployment_forbidsNonOwnerNonAdminUsers")
    void startRequestDeployment_forbidsNonOwner() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf);
        req.setOwner("alice");
        requestRepository.save(req);
        helper.seedTask(req);

        mockMvc.perform(post(BASE + "/" + rf.getId() + "/requests/" + req.getId() + "/start")
                        .header("X-User-Id", "emp-002")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("markRequestFailed_forbidsNonOwnerNonAdminUsers")
    void markRequestFailed_forbidsNonOwner() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf);
        req.setOwner("alice");
        requestRepository.save(req);
        helper.seedTask(req);

        mockMvc.perform(post(BASE + "/" + rf.getId() + "/requests/" + req.getId() + "/fail")
                        .header("X-User-Id", "emp-002")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("archiveRequestRundown_archivesWholeFlow_whenLastActiveStageArchived")
    void archiveRequestRundown_archivesWholeFlow_whenLastActiveStageArchived() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf);
        helper.seedTask(req);

        mockMvc.perform(post(BASE + "/" + rf.getId() + "/requests/" + req.getId() + "/archive")
                        .header("X-User-Id", "emp-001")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releaseFlowId").value(rf.getId()))
                .andExpect(jsonPath("$.requestId").value(req.getId()))
                .andExpect(jsonPath("$.stage").value("SIT"))
                .andExpect(jsonPath("$.requestArchived").value(true))
                .andExpect(jsonPath("$.releaseFlowArchived").value(true))
                .andExpect(jsonPath("$.activeRequestCount").value(0));

        mockMvc.perform(get(BASE + "/" + rf.getId())
                        .header("X-User-Id", "emp-001")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(BASE + "/" + rf.getId())
                        .param("includeArchived", "true")
                        .header("X-User-Id", "emp-001")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivedAt", notNullValue()));
    }

    @Test
    @DisplayName("archiveRequestRundown_keepsFlow_andMovesCurrentStage_whenOtherStagesRemain")
    void archiveRequestRundown_keepsFlow_andMovesCurrentStage_whenOtherStagesRemain() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request sit = helper.seedRequest(rf, Stage.SIT, RequestStatus.Pending);
        Request uat = helper.seedRequest(rf, Stage.UAT, RequestStatus.Pending);

        mockMvc.perform(post(BASE + "/" + rf.getId() + "/requests/" + sit.getId() + "/archive")
                        .header("X-User-Id", "emp-001")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestArchived").value(true))
                .andExpect(jsonPath("$.releaseFlowArchived").value(false))
                .andExpect(jsonPath("$.activeRequestCount").value(1));

        mockMvc.perform(get(BASE + "/" + rf.getId())
                        .header("X-User-Id", "emp-001")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStage").value("UAT"))
                .andExpect(jsonPath("$.requests.length()").value(1))
                .andExpect(jsonPath("$.requests[0].id").value(uat.getId()));

        mockMvc.perform(get(BASE + "/" + rf.getId())
                        .param("includeArchived", "true")
                        .header("X-User-Id", "emp-001")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requests.length()").value(2));
    }

    @Test
    @DisplayName("purgeArchivedRequestRundown_deletesArchivedRundown_andRemovesFlowWhenLastRequest")
    void purgeArchivedRequestRundown_deletesArchivedRundown_andRemovesFlowWhenLastRequest() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf);
        helper.seedTask(req);

        mockMvc.perform(post(BASE + "/" + rf.getId() + "/requests/" + req.getId() + "/archive")
                        .header("X-User-Id", "emp-001")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isOk());

        mockMvc.perform(delete(BASE + "/" + rf.getId() + "/requests/" + req.getId() + "/purge")
                        .header("X-User-Id", "emp-001")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releaseFlowId").value(rf.getId()))
                .andExpect(jsonPath("$.requestId").value(req.getId()))
                .andExpect(jsonPath("$.stage").value("SIT"))
                .andExpect(jsonPath("$.releaseFlowDeleted").value(true))
                .andExpect(jsonPath("$.remainingRequestCount").value(0))
                .andExpect(jsonPath("$.activeRequestCount").value(0));

        mockMvc.perform(get(BASE + "/" + rf.getId())
                        .param("includeArchived", "true")
                        .header("X-User-Id", "emp-001")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isNotFound());
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
