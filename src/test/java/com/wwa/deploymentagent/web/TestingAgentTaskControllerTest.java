package com.wwa.deploymentagent.web;

import com.wwa.deploymentagent.contracts.AgentId;
import com.wwa.deploymentagent.contracts.enums.TaskStatus;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlow;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("TestingAgentTaskController API contract")
class TestingAgentTaskControllerTest {

    private static final String BASE = "/api/testing-agent/tasks";

    @Autowired private MockMvc mockMvc;
    @Autowired private TestDataHelper helper;

    @Test
    @DisplayName("GET /tasks?requestId=X returns tasks for request")
    void listByRequest_returnsTasks() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf, AgentId.TESTING_AGENT);
        helper.seedTask(req);

        mockMvc.perform(get(BASE)
                        .param("requestId", req.getId())
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("GET /tasks/:id returns task")
    void getById_returnsTask() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf, AgentId.TESTING_AGENT);
        Task task = helper.seedTask(req);

        mockMvc.perform(get(BASE + "/" + task.getId())
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(task.getId()));
    }

    @Test
    @DisplayName("GET /tasks/:id/executions returns empty list when no executions")
    void getExecutions_returnsEmptyList() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf, AgentId.TESTING_AGENT);
        Task task = helper.seedTask(req);

        mockMvc.perform(get(BASE + "/" + task.getId() + "/executions")
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("PUT /tasks/:id/input updates task input")
    void editInput_updatesTask() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf, AgentId.TESTING_AGENT);
        Task task = helper.seedTask(req);

        mockMvc.perform(put(BASE + "/" + task.getId() + "/input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"script\": \"test.sh\"}")
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(task.getId()));
    }

    @Test
    @DisplayName("PUT /tasks/:id/input preserves unspecified input fields for Testing Agent tasks")
    void editInput_partialUpdate_preservesExistingFields() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf, AgentId.TESTING_AGENT);
        Task task = helper.seedTask(req);

        mockMvc.perform(put(BASE + "/" + task.getId() + "/input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"script\": \"test.sh\"}")
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inputParameters.script").value("test.sh"))
                .andExpect(jsonPath("$.inputParameters.parameters").value("--env staging"));
    }
}
