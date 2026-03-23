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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("T13.5 - Authorization security tests")
class SecurityTest {

    private static final String RELEASE_FLOWS = "/api/deployment-agent/release-flows";
    private static final String TASKS         = "/api/deployment-agent/tasks";
    private static final String CONFIG        = "/api/deployment-agent/config";
    private static final String UPLOAD        = "/api/deployment-agent/upload";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestDataHelper helper;

    // ─── Missing auth headers → 401 ──────────────────────────────────────────

    @Test
    @DisplayName("anyEndpoint_missingAuthHeaders_returns401 - no headers at all returns 401")
    void anyEndpoint_missingAuthHeaders_returns401() throws Exception {
        mockMvc.perform(get(RELEASE_FLOWS))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("anyEndpoint_missingUserId_returns401 - only X-User-Role header returns 401")
    void anyEndpoint_missingUserId_returns401() throws Exception {
        mockMvc.perform(get(RELEASE_FLOWS)
                        .header("X-User-Role", "TL"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("anyEndpoint_missingRole_returns401 - only X-User-Id header returns 401")
    void anyEndpoint_missingRole_returns401() throws Exception {
        mockMvc.perform(get(RELEASE_FLOWS)
                        .header("X-User-Id", "user1"))
                .andExpect(status().isUnauthorized());
    }

    // ─── Role-based access: wrong role → 403 ─────────────────────────────────

    @Test
    @DisplayName("editInput_wrongRole_returns403 - PUT /tasks/{id}/input with non-owner TL returns 403")
    void editInput_wrongRole_returns403() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf);
        Task task = helper.seedTask(req, TaskStatus.Pending);

        mockMvc.perform(put(TASKS + "/" + task.getId() + "/input")
                        .header("X-User-Id", "emp-002")
                        .header("X-User-Role", "TL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"script\": \"deploy.sh\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("config_upsert_nonAdminRole_returns403 - POST /config with TL returns 403")
    void config_upsert_nonAdminRole_returns403() throws Exception {
        mockMvc.perform(post(CONFIG)
                        .header("X-User-Id", "tl-user")
                        .header("X-User-Role", "TL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\": \"jenkins_url\", \"value\": \"http://jenkins\", \"description\": \"test\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("upload_devopsAdminRole_returns403 - POST /upload multipart with DEVOPS_ADMIN returns 403")
    void upload_devopsAdminRole_returns403() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "dummy-content".getBytes());

        mockMvc.perform(multipart(UPLOAD)
                        .file(file)
                        .param("stage", "SIT")
                        .header("X-User-Id", "admin-user")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("decision_nonOwnerRole_returns403 - POST /tasks/{id}/decision with non-owner developer returns 403")
    void decision_developerRole_returns403() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf);
        Task task = helper.seedTask(req, TaskStatus.Awaiting_Review);

        mockMvc.perform(post(TASKS + "/" + task.getId() + "/decision")
                        .header("X-User-Id", "dev-user")
                        .header("X-User-Role", "DEVELOPER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\": \"approve\"}"))
                .andExpect(status().isForbidden());
    }
}
