package com.wwa.deploymentagent.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwa.deploymentagent.contracts.enums.RequestStatus;
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
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
    private ObjectMapper objectMapper;

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
        helper.seedRequest(rf, "SIT", RequestStatus.Completed);
        helper.seedRequest(rf, "UAT", RequestStatus.Running);

        mockMvc.perform(get(BASE)
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].stageStatuses.SIT").value("Completed"))
                .andExpect(jsonPath("$.data[0].stageStatuses.UAT").value("Running"))
                .andExpect(jsonPath("$.data[0].stageStatuses.PROD").doesNotExist());
    }

    @Test
    @DisplayName("list_viewStitched_attemptViewSwitchesBetweenLatestAndHistoryForRepeatedStageAttempts")
    void list_viewStitched_attemptViewSwitchesBetweenLatestAndHistoryForRepeatedStageAttempts() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        helper.seedRequest(rf, "SIT", RequestStatus.Failed);
        helper.seedRequest(rf, "SIT", RequestStatus.Completed);

        mockMvc.perform(get(BASE)
                        .param("view", "stitched")
                        .param("attemptView", "latest")
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].stageStatuses.SIT").value("Completed"));

        mockMvc.perform(get(BASE)
                        .param("view", "stitched")
                        .param("attemptView", "history")
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].stageStatuses.SIT").value("Failed"));
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
                "PROJ-002", "PowerCARD", "sit-powercard-0001", "sit-powercard-0001", "SIT");
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
        ReleaseFlow sitFlow = releaseFlowService.create("PROJ-STITCH", "Stitched Project", "sit-01", "sit-01", "SIT");
        Request sitRequest = helper.seedRequest(sitFlow, "SIT", RequestStatus.Completed);
        sitRequest.setApplication("AMH HCC");
        sitRequest.setOwner("alice");
        requestRepository.save(sitRequest);

        ReleaseFlow uatFlow = releaseFlowService.create("PROJ-STITCH", "Stitched Project", "uat-01", "uat-01", "UAT");
        Request uatRequest = helper.seedRequest(uatFlow, "UAT", RequestStatus.Pending);
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
                .andExpect(jsonPath("$.data[0].stagesPresent", hasItem("SIT")))
                .andExpect(jsonPath("$.data[0].stagesPresent", hasItem("UAT")))
                .andExpect(jsonPath("$.data[0].stagesPresent", not(hasItem("PROD"))));
    }

    @Test
    @DisplayName("list_viewStitched_groupsInfixStageIdentifiersAndKeepsLatestSitRetry")
    void list_viewStitched_groupsInfixStageIdentifiersAndKeepsLatestSitRetry() throws Exception {
        ReleaseFlow sitAttemptOneFlow = releaseFlowService.create(
                "PROJ-STITCH-INFIX",
                "Infix Project",
                "leo-sit-01",
                "leo-sit-01",
                "SIT");
        Request sitAttemptOne = helper.seedRequest(sitAttemptOneFlow, "SIT", RequestStatus.Completed);
        sitAttemptOne.setAttemptNumber(1);
        requestRepository.save(sitAttemptOne);

        ReleaseFlow sitAttemptTwoFlow = releaseFlowService.create(
                "PROJ-STITCH-INFIX",
                "Infix Project",
                "leo-sit-02",
                "leo-sit-02",
                "SIT");
        Request sitAttemptTwo = helper.seedRequest(sitAttemptTwoFlow, "SIT", RequestStatus.Pending);
        sitAttemptTwo.setAttemptNumber(2);
        requestRepository.save(sitAttemptTwo);

        ReleaseFlow uatFlow = releaseFlowService.create(
                "PROJ-STITCH-INFIX",
                "Infix Project",
                "leo-uat-01",
                "leo-uat-01",
                "UAT");
        helper.seedRequest(uatFlow, "UAT", RequestStatus.Pending);

        ReleaseFlow prodFlow = releaseFlowService.create(
                "PROJ-STITCH-INFIX",
                "Infix Project",
                "leo-prod-01",
                "leo-prod-01",
                "PROD");
        helper.seedRequest(prodFlow, "PROD", RequestStatus.Pending);

        mockMvc.perform(get(BASE)
                        .param("view", "stitched")
                        .param("attemptView", "latest")
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].stitched").value(true))
                .andExpect(jsonPath("$.data[0].linkedReleaseCount").value(4))
                .andExpect(jsonPath("$.data[0].currentStage").value("PROD"))
                .andExpect(jsonPath("$.data[0].stageStatuses.SIT").value("Pending"))
                .andExpect(jsonPath("$.data[0].stagesPresent", hasItem("UAT")))
                .andExpect(jsonPath("$.data[0].stagesPresent", hasItem("PROD")));
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
        Request first = helper.seedRequest(rf, "SIT", RequestStatus.Failed);
        Request second = helper.seedRequest(rf, "SIT", RequestStatus.Pending);
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
        ReleaseFlow sitFlow = releaseFlowService.create("PROJ-LINK", "Linked Project", "sit-01", "sit-01", "SIT");
        Request sitRequest = helper.seedRequest(sitFlow, "SIT", RequestStatus.Pending);
        helper.seedTask(sitRequest);

        ReleaseFlow prodFlow = releaseFlowService.create("PROJ-LINK", "Linked Project", "prod-01", "prod-01", "PROD");
        Request prodRequest = helper.seedRequest(prodFlow, "PROD", RequestStatus.Pending);
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
    @DisplayName("createFromTemplate_createsRundownAndExposesTemplateTaskMetadata")
    void createFromTemplate_createsRundownAndExposesTemplateTaskMetadata() throws Exception {
        String createResponse = mockMvc.perform(post(BASE + "/from-template")
                        .header("X-User-Id", "emp-003")
                        .header("X-User-Role", "DEVOPS_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateId": "tpl-002",
                                  "templateName": "AMH-HCC-SIT-TEMPLATE",
                                  "projectName": "AMH HCC",
                                  "stage": "SIT",
                                  "releaseId": "amh-hcc-sit-01",
                                  "snowGroup": "HTSA-CSI-HCC-AMH-PRJ",
                                  "application": "AMH HCC",
                                  "agent": "Deployment Agent",
                                  "site": "HK",
                                  "owner": "Carol Lee",
                                  "tasks": [
                                    {
                                      "category": "release preparation",
                                      "taskName": "Prepare Deployment Package",
                                      "step": 1,
                                      "stepName": "collect sit release files",
                                      "type": "MANUAL",
                                      "critical": false,
                                      "owner": "Carol Lee",
                                      "estDurationMinutes": 10
                                    },
                                    {
                                      "category": "release",
                                      "taskName": "Deploy Application",
                                      "step": 2,
                                      "stepName": "deploy sit package",
                                      "type": "AUTO",
                                      "critical": true,
                                      "owner": "Carol Lee",
                                      "estDurationMinutes": 20,
                                      "dependencies": "Prepare Deployment Package"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releaseFlowId").isString())
                .andExpect(jsonPath("$.releaseId").value("amh-hcc-sit-01"))
                .andExpect(jsonPath("$.stage").value("SIT"))
                .andExpect(jsonPath("$.taskCount").value(2))
                .andExpect(jsonPath("$.snowGroup").value("HTSA-CSI-HCC-AMH-PRJ"))
                .andExpect(jsonPath("$.application").value("AMH HCC"))
                .andExpect(jsonPath("$.agent").value("Deployment Agent"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String flowId = objectMapper.readTree(createResponse).path("releaseFlowId").asText();

        mockMvc.perform(get(BASE + "/" + flowId)
                        .header("X-User-Id", "emp-003")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectName").value("AMH HCC"))
                .andExpect(jsonPath("$.releaseId").value("amh-hcc-sit-01"))
                .andExpect(jsonPath("$.currentStage").value("SIT"))
                .andExpect(jsonPath("$.requests.length()").value(1))
                .andExpect(jsonPath("$.requests[0].requestStatus").value("Pending"))
                .andExpect(jsonPath("$.requests[0].site").value("HK"))
                .andExpect(jsonPath("$.requests[0].owner").value("Carol Lee"))
                .andExpect(jsonPath("$.requests[0].estimatedRemainingMinutes").value(30))
                .andExpect(jsonPath("$.requests[0].tasks.length()").value(2))
                .andExpect(jsonPath("$.requests[0].tasks[0].taskGroupName").value("Prepare Deployment Package"))
                .andExpect(jsonPath("$.requests[0].tasks[0].taskName").value("collect sit release files"))
                .andExpect(jsonPath("$.requests[0].tasks[0].category").value("release preparation"))
                .andExpect(jsonPath("$.requests[0].tasks[1].dependencies").value("Prepare Deployment Package"))
                .andExpect(jsonPath("$.requests[0].tasks[1].critical").value(true));
    }

    @Test
    @DisplayName("createFromTemplate_nonReleaseOperator_returns403")
    void createFromTemplate_nonReleaseOperator_returns403() throws Exception {
        mockMvc.perform(post(BASE + "/from-template")
                        .header("X-User-Id", "emp-009")
                        .header("X-User-Role", "AUDIT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateId": "tpl-002",
                                  "templateName": "AMH-HCC-SIT-TEMPLATE",
                                  "projectName": "AMH HCC",
                                  "stage": "SIT",
                                  "tasks": [
                                    {
                                      "category": "release preparation",
                                      "taskName": "Prepare Deployment Package",
                                      "step": 1,
                                      "stepName": "collect sit release files",
                                      "type": "MANUAL",
                                      "critical": false,
                                      "owner": "Carol Lee",
                                      "estDurationMinutes": 10
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("createFromTemplate_missingReleaseIdentifier_returns400")
    void createFromTemplate_missingReleaseIdentifier_returns400() throws Exception {
        mockMvc.perform(post(BASE + "/from-template")
                        .header("X-User-Id", "emp-003")
                        .header("X-User-Role", "DEVOPS_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateId": "tpl-002",
                                  "templateName": "AMH-HCC-SIT-TEMPLATE",
                                  "projectName": "AMH HCC",
                                  "stage": "SIT",
                                  "tasks": [
                                    {
                                      "category": "release preparation",
                                      "taskName": "Prepare Deployment Package",
                                      "step": 1,
                                      "stepName": "collect sit release files",
                                      "type": "MANUAL",
                                      "critical": false,
                                      "owner": "Carol Lee",
                                      "estDurationMinutes": 10
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Release identifier is required."));
    }

    @Test
    @DisplayName("createFromTemplate_invalidReleaseIdentifierShape_returns400")
    void createFromTemplate_invalidReleaseIdentifierShape_returns400() throws Exception {
        mockMvc.perform(post(BASE + "/from-template")
                        .header("X-User-Id", "emp-003")
                        .header("X-User-Role", "DEVOPS_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateId": "tpl-002",
                                  "templateName": "AMH-HCC-SIT-TEMPLATE",
                                  "projectName": "AMH HCC",
                                  "stage": "SIT",
                                  "releaseId": "amh-hcc-uat-00",
                                  "tasks": [
                                    {
                                      "category": "release preparation",
                                      "taskName": "Prepare Deployment Package",
                                      "step": 1,
                                      "stepName": "collect sit release files",
                                      "type": "MANUAL",
                                      "critical": false,
                                      "owner": "Carol Lee",
                                      "estDurationMinutes": 10
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Release identifier must match xxx-sit-01 / xxx-uat-01 / xxx-prod-01."));
    }

    @Test
    @DisplayName("createFromTemplate_releaseIdentifierStageMustMatchSelectedStage")
    void createFromTemplate_releaseIdentifierStageMustMatchSelectedStage() throws Exception {
        mockMvc.perform(post(BASE + "/from-template")
                        .header("X-User-Id", "emp-003")
                        .header("X-User-Role", "DEVOPS_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateId": "tpl-002",
                                  "templateName": "AMH-HCC-SIT-TEMPLATE",
                                  "projectName": "AMH HCC",
                                  "stage": "SIT",
                                  "releaseId": "amh-hcc-uat-01",
                                  "tasks": [
                                    {
                                      "category": "release preparation",
                                      "taskName": "Prepare Deployment Package",
                                      "step": 1,
                                      "stepName": "collect sit release files",
                                      "type": "MANUAL",
                                      "critical": false,
                                      "owner": "Carol Lee",
                                      "estDurationMinutes": 10
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Release identifier stage segment must match the selected stage 'SIT'."));
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
        Request sit = helper.seedRequest(rf, "SIT", RequestStatus.Pending);
        Request uat = helper.seedRequest(rf, "UAT", RequestStatus.Pending);

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
