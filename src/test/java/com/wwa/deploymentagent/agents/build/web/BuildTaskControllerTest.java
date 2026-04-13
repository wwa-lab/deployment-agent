package com.wwa.deploymentagent.agents.build.web;

import com.wwa.deploymentagent.contracts.AgentId;
import com.wwa.deploymentagent.contracts.enums.RequestStatus;
import com.wwa.deploymentagent.contracts.enums.TaskStatus;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlow;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlowService;
import com.wwa.deploymentagent.domain.releaseflow.Request;
import com.wwa.deploymentagent.domain.task.Task;
import com.wwa.deploymentagent.helper.TestDataHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("BuildTaskController")
class BuildTaskControllerTest {

    private static final String BASE = "/api/build-agent/tasks";

    @Autowired private MockMvc mockMvc;
    @Autowired private TestDataHelper helper;
    @Autowired private ReleaseFlowService releaseFlowService;

    private ReleaseFlow createFlow() {
        return releaseFlowService.create("PROJ-BUILD-DOCS", "Build Docs", "dev-build-docs", "dev-build-docs", "DEV");
    }

    @Test
    @DisplayName("PUT /tasks/{id}/docs saves task doc overrides into customFields")
    void editDocs_updatesCustomFields() throws Exception {
        ReleaseFlow flow = createFlow();
        Request request = helper.seedRequest(flow, "DEV", RequestStatus.Pending, AgentId.BUILD_AGENT);
        Task task = helper.seedTask(request, TaskStatus.Pending);

        mockMvc.perform(put(BASE + "/" + task.getId() + "/docs")
                        .header("X-User-Id", "emp-003")
                        .header("X-User-Role", "DEVOPS_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inputs": [
                                    {
                                      "label": "Requirement Package",
                                      "url": "https://github.com/example/requirement.md",
                                      "note": "Project-specific requirement baseline",
                                      "required": true
                                    }
                                  ],
                                  "outputs": [
                                    {
                                      "label": "Technical Design",
                                      "url": "https://github.com/example/design.md",
                                      "required": true
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customFields.taskDocs.inputs[0].label").value("Requirement Package"))
                .andExpect(jsonPath("$.customFields.taskDocs.inputs[0].url").value("https://github.com/example/requirement.md"))
                .andExpect(jsonPath("$.customFields.taskDocs.outputs[0].label").value("Technical Design"));
    }
}
