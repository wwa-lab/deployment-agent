package com.wwa.deploymentagent.web;

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
@DisplayName("T13.3 - TaskController API contract")
class TaskControllerTest {

    private static final String BASE = "/api/deployment-agent/tasks";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestDataHelper helper;

    // ─── listByRequest ────────────────────────────────────────────────────────

    @Test
    @DisplayName("listByRequest_returnsTasks - GET /tasks?requestId=X returns list size 1")
    void listByRequest_returnsTasks() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf);
        helper.seedTask(req);

        mockMvc.perform(get(BASE)
                        .param("requestId", req.getId())
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "DEVELOPER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ─── getById ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getById_returnsTask - GET /tasks/{id} returns 200 with correct id")
    void getById_returnsTask() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf);
        Task task = helper.seedTask(req);

        mockMvc.perform(get(BASE + "/" + task.getId())
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(task.getId()));
    }

    @Test
    @DisplayName("getById_unknownId_returns404 - GET /tasks/unknown returns 404")
    void getById_unknownId_returns404() throws Exception {
        mockMvc.perform(get(BASE + "/unknown")
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isNotFound());
    }

    // ─── editInput ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("editInput_tlRole_succeeds - PUT /tasks/{id}/input with TL returns 200")
    void editInput_tlRole_succeeds() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf);
        Task task = helper.seedTask(req, TaskStatus.Pending);

        mockMvc.perform(put(BASE + "/" + task.getId() + "/input")
                        .header("X-User-Id", "tl-user")
                        .header("X-User-Role", "TL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"script\": \"new_deploy.sh\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("editInput_developerRole_returns403 - PUT /tasks/{id}/input with DEVELOPER returns 403")
    void editInput_developerRole_returns403() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf);
        Task task = helper.seedTask(req, TaskStatus.Pending);

        mockMvc.perform(put(BASE + "/" + task.getId() + "/input")
                        .header("X-User-Id", "dev-user")
                        .header("X-User-Role", "DEVELOPER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"script\": \"new_deploy.sh\"}"))
                .andExpect(status().isForbidden());
    }

    // ─── getExecutions ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getExecutions_returnsEmptyList - GET /tasks/{id}/executions returns 200 with empty list")
    void getExecutions_returnsEmptyList() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf);
        Task task = helper.seedTask(req);

        mockMvc.perform(get(BASE + "/" + task.getId() + "/executions")
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
