package com.wwa.agenthub.web;

import com.wwa.agenthub.contracts.AgentId;
import com.wwa.agenthub.domain.releaseflow.ReleaseFlow;
import com.wwa.agenthub.domain.releaseflow.Request;
import com.wwa.agenthub.helper.TestDataHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("ProjectAgentReleaseFlowController API contract")
class ProjectAgentReleaseFlowControllerTest {

    private static final String BASE = "/api/project-agent/release-flows";

    @Autowired private MockMvc mockMvc;
    @Autowired private TestDataHelper helper;

    @Test
    @DisplayName("list returns only project-agent flows")
    void list_returnsOnlyProjectAgentFlows() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        rf.setCurrentStage("REQUIREMENT");
        helper.seedRequest(rf, "REQUIREMENT", com.wwa.agenthub.contracts.enums.RequestStatus.Pending, AgentId.PROJECT_AGENT);

        mockMvc.perform(get(BASE)
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    @DisplayName("list does not return build-agent flows")
    void list_excludesOtherAgents() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        rf.setCurrentStage("REQUIREMENT");
        helper.seedRequest(rf, "REQUIREMENT", com.wwa.agenthub.contracts.enums.RequestStatus.Pending, AgentId.BUILD_AGENT);

        mockMvc.perform(get(BASE)
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @DisplayName("list accepts project lifecycle stage filter")
    void list_filtersByProjectStage() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        rf.setCurrentStage("TECHNICAL_DESIGN");
        helper.seedRequest(rf, "TECHNICAL_DESIGN", com.wwa.agenthub.contracts.enums.RequestStatus.Pending, AgentId.PROJECT_AGENT);

        mockMvc.perform(get(BASE)
                        .param("stage", "TECHNICAL_DESIGN")
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    @DisplayName("createFromTemplate_acceptsStageFreeLifecycleIdentifier")
    void createFromTemplate_acceptsStageFreeLifecycleIdentifier() throws Exception {
        mockMvc.perform(post(BASE + "/from-template")
                        .header("X-User-Id", "emp-003")
                        .header("X-User-Role", "DEVOPS_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateId": "tpl-project-001",
                                  "templateName": "Project Agent Lifecycle Baseline",
                                  "projectName": "AMH HCC",
                                  "stage": "TECHNICAL_DESIGN",
                                  "releaseId": "amh-hcc-project-01",
                                  "tasks": [
                                    {
                                      "category": "requirement",
                                      "taskName": "Assess Requirement Impact",
                                      "step": 1,
                                      "stepName": "confirm project scope",
                                      "type": "MANUAL",
                                      "critical": true,
                                      "owner": "Carol Lee",
                                      "estDurationMinutes": 10
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("REQUIREMENT"))
                .andExpect(jsonPath("$.releaseId").value("amh-hcc-project-01"));
    }

    @Test
    @DisplayName("createFromTemplate_acceptsFlexibleProjectLifecycleIdentifier")
    void createFromTemplate_acceptsFlexibleProjectLifecycleIdentifier() throws Exception {
        mockMvc.perform(post(BASE + "/from-template")
                        .header("X-User-Id", "emp-003")
                        .header("X-User-Role", "DEVOPS_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateId": "tpl-project-001",
                                  "templateName": "Project Agent Lifecycle Baseline",
                                  "projectName": "AMH HCC",
                                  "stage": "REQUIREMENT",
                                  "releaseId": "amh-hcc-requirement-01",
                                  "tasks": [
                                    {
                                      "category": "requirement",
                                      "taskName": "Assess Requirement Impact",
                                      "step": 1,
                                      "stepName": "confirm project scope",
                                      "type": "MANUAL",
                                      "critical": true,
                                      "owner": "Carol Lee",
                                      "estDurationMinutes": 10
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("REQUIREMENT"))
                .andExpect(jsonPath("$.releaseId").value("amh-hcc-requirement-01"));
    }

    @Test
    @DisplayName("createFromTemplate_generatesUuidWhenLifecycleIdentifierMissing")
    void createFromTemplate_generatesUuidWhenLifecycleIdentifierMissing() throws Exception {
        MvcResult result = mockMvc.perform(post(BASE + "/from-template")
                        .header("X-User-Id", "emp-003")
                        .header("X-User-Role", "DEVOPS_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateId": "tpl-project-001",
                                  "templateName": "Project Agent Lifecycle Baseline",
                                  "projectName": "AMH HCC",
                                  "stage": "REQUIREMENT",
                                  "tasks": [
                                    {
                                      "category": "requirement",
                                      "taskName": "Assess Requirement Impact",
                                      "step": 1,
                                      "stepName": "confirm project scope",
                                      "type": "MANUAL",
                                      "critical": true,
                                      "owner": "Carol Lee",
                                      "estDurationMinutes": 10
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("REQUIREMENT"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).containsPattern("\"releaseId\":\"[0-9a-f\\-]{36}\"");
    }

    @Test
    @DisplayName("detail derives current lifecycle stage from task categories")
    void detail_derivesCurrentLifecycleStageFromTaskCategories() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        rf.setCurrentStage("REQUIREMENT");
        Request request = helper.seedRequest(rf, "REQUIREMENT", com.wwa.agenthub.contracts.enums.RequestStatus.Running, AgentId.PROJECT_AGENT);
        helper.seedTask(
                request,
                com.wwa.agenthub.contracts.enums.TaskStatus.Approved,
                true,
                "Download Bulletin",
                "download bulletin",
                Map.of("activity_category", "requirement"));
        helper.seedTask(
                request,
                com.wwa.agenthub.contracts.enums.TaskStatus.Ready_For_Execution,
                true,
                "Draft Functional Change Background",
                "draft functional change background",
                Map.of("activity_category", "functional design"));

        mockMvc.perform(get(BASE + "/" + rf.getId())
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStage").value("FUNCTIONAL_DESIGN"));
    }

    @Test
    @DisplayName("list derives lifecycle stage statuses from task categories")
    void list_derivesLifecycleStageStatusesFromTaskCategories() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        rf.setCurrentStage("REQUIREMENT");
        Request request = helper.seedRequest(rf, "REQUIREMENT", com.wwa.agenthub.contracts.enums.RequestStatus.Running, AgentId.PROJECT_AGENT);
        helper.seedTask(
                request,
                com.wwa.agenthub.contracts.enums.TaskStatus.Approved,
                true,
                "Download Bulletin",
                "download bulletin",
                Map.of("activity_category", "requirement"));
        helper.seedTask(
                request,
                com.wwa.agenthub.contracts.enums.TaskStatus.Ready_For_Execution,
                true,
                "Draft Functional Change Background",
                "draft functional change background",
                Map.of("activity_category", "functional design"));

        mockMvc.perform(get(BASE)
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].currentStage").value("FUNCTIONAL_DESIGN"))
                .andExpect(jsonPath("$.data[0].stageStatuses.REQUIREMENT").value("Completed"))
                .andExpect(jsonPath("$.data[0].stageStatuses.FUNCTIONAL_DESIGN").value("Running"));
    }

    @Test
    @DisplayName("list initializes project lifecycle tasks for current stage derivation")
    void list_initializesProjectLifecycleTasksForCurrentStageDerivation() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        rf.setCurrentStage("REQUIREMENT");
        Request request = helper.seedRequest(
                rf,
                "REQUIREMENT",
                com.wwa.agenthub.contracts.enums.RequestStatus.Running,
                AgentId.PROJECT_AGENT);
        helper.seedTask(
                request,
                com.wwa.agenthub.contracts.enums.TaskStatus.Approved,
                true,
                "Download Bulletin",
                "download bulletin",
                Map.of("activity_category", "requirement"));
        helper.seedTask(
                request,
                com.wwa.agenthub.contracts.enums.TaskStatus.Ready_For_Execution,
                true,
                "Draft Functional Change Background",
                "draft functional change background",
                Map.of("activity_category", "functional design"));

        mockMvc.perform(get(BASE)
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].currentStage").value("FUNCTIONAL_DESIGN"));
    }
}
