package com.wwa.deploymentagent.web;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.enums.ExecutionType;
import com.wwa.deploymentagent.contracts.enums.TaskStatus;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlow;
import com.wwa.deploymentagent.domain.releaseflow.Request;
import com.wwa.deploymentagent.domain.task.CreateTaskInput;
import com.wwa.deploymentagent.domain.task.Task;
import com.wwa.deploymentagent.domain.task.TaskService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @Autowired
    private TaskService taskService;

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
        Task task = helper.seedTask(req, TaskStatus.Pending, true);

        mockMvc.perform(get(BASE + "/" + task.getId())
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(task.getId()))
                .andExpect(jsonPath("$.critical").value(true));
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
    @DisplayName("editInput_ownerRole_succeeds - PUT /tasks/{id}/input with owner returns 200")
    void editInput_ownerRole_succeeds() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf);
        Task task = helper.seedTask(req, TaskStatus.Pending);

        mockMvc.perform(put(BASE + "/" + task.getId() + "/input")
                        .header("X-User-Id", "emp-001")
                        .header("X-User-Role", "DEVELOPER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"script\": \"new_deploy.sh\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("editInput_nonOwnerRole_returns403 - PUT /tasks/{id}/input with non-owner developer returns 403")
    void editInput_nonOwnerRole_returns403() throws Exception {
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

    @Test
    @DisplayName("editInput_adminRole_succeeds - PUT /tasks/{id}/input with DEVOPS_ADMIN returns 200")
    void editInput_adminRole_succeeds() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf);
        Task task = helper.seedTask(req, TaskStatus.Pending);

        mockMvc.perform(put(BASE + "/" + task.getId() + "/input")
                        .header("X-User-Id", "emp-003")
                        .header("X-User-Role", "DEVOPS_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"script\": \"admin_deploy.sh\"}"))
                .andExpect(status().isOk());
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

    @Test
    @DisplayName("startManualExecution_ownerRole_succeeds - POST /tasks/{id}/start-manual returns Executing")
    void startManualExecution_ownerRole_succeeds() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf);
        Task task = createManualTask(req, TaskStatus.Ready_For_Execution);

        mockMvc.perform(post(BASE + "/" + task.getId() + "/start-manual")
                        .header("X-User-Id", "emp-001")
                        .header("X-User-Role", "DEVELOPER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskStatus").value("Executing"));
    }

    @Test
    @DisplayName("startManualExecution_nonOwnerRole_returns403 - POST /tasks/{id}/start-manual with non-owner returns 403")
    void startManualExecution_nonOwnerRole_returns403() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf);
        Task task = createManualTask(req, TaskStatus.Ready_For_Execution);

        mockMvc.perform(post(BASE + "/" + task.getId() + "/start-manual")
                        .header("X-User-Id", "dev-user")
                        .header("X-User-Role", "DEVELOPER"))
                .andExpect(status().isForbidden());
    }

    private Task createManualTask(Request request, TaskStatus initialStatus) {
        Task task = taskService.create(new CreateTaskInput(
                request,
                "TG-MANUAL",
                "Manual Group",
                1,
                "manual-step",
                ExecutionType.MANUAL,
                false,
                java.util.Map.of("script", "deploy.sh", "parameters", "--env uat"),
                null,
                "alice",
                null,
                null,
                null));
        if (initialStatus == TaskStatus.Ready_For_Execution) {
            return taskService.updateStatus(
                    task.getId(),
                    TaskStatus.Ready_For_Execution,
                    new UserContext("emp-001", "DEVELOPER"),
                    null);
        }
        return task;
    }
}
