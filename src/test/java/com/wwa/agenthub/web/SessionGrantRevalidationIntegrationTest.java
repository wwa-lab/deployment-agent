package com.wwa.agenthub.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwa.agenthub.contracts.AccessScope;
import com.wwa.agenthub.contracts.dto.LoginRequestDto;
import com.wwa.agenthub.contracts.enums.AccessGrantStatus;
import com.wwa.agenthub.contracts.enums.CapabilityType;
import com.wwa.agenthub.contracts.enums.TaskStatus;
import com.wwa.agenthub.domain.auth.AccessGrant;
import com.wwa.agenthub.domain.auth.AccessGrantRepository;
import com.wwa.agenthub.domain.releaseflow.ReleaseFlow;
import com.wwa.agenthub.domain.releaseflow.Request;
import com.wwa.agenthub.domain.task.Task;
import com.wwa.agenthub.helper.TestDataHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.auth.session-grant-revalidation-enabled=true",
        "spring.datasource.url=jdbc:h2:mem:session-revalidation;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=Oracle"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionGrantRevalidationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AccessGrantRepository accessGrantRepository;
    @Autowired private TestDataHelper helper;

    @Test
    void existingSessionImmediatelyUsesCurrentScopeAndSuspensionState() throws Exception {
        ReleaseFlow flow = helper.seedReleaseFlow("session-revoke");
        Request request = helper.seedRequest(flow);
        Task task = helper.seedIntegrationTask(
                request, TaskStatus.Ready_For_Execution, CapabilityType.MANUAL, "emp-002");

        MvcResult login = mockMvc.perform(post("/api/platform/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequestDto("emp-002", "password"))))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession();

        AccessGrant grant = accessGrantRepository.findById("emp-002").orElseThrow();
        AccessGrantStatus originalStatus = grant.getGrantStatus();
        List<AccessScope> originalScopes = List.copyOf(grant.getScopeGrants());
        try {
            mockMvc.perform(get("/api/v1/integration/tasks/{id}", task.getId()).session(session))
                    .andExpect(status().isOk());

            grant.setScopeGrants(List.of(new AccessScope("outside", "outside")));
            accessGrantRepository.saveAndFlush(grant);
            mockMvc.perform(get("/api/v1/integration/tasks/{id}", task.getId()).session(session))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("TASK_NOT_FOUND"));

            grant = accessGrantRepository.findById("emp-002").orElseThrow();
            grant.setGrantStatus(AccessGrantStatus.SUSPENDED);
            accessGrantRepository.saveAndFlush(grant);
            mockMvc.perform(get("/api/v1/integration/tasks").session(session))
                    .andExpect(status().isUnauthorized());
        } finally {
            AccessGrant restored = accessGrantRepository.findById("emp-002").orElseThrow();
            restored.setGrantStatus(originalStatus);
            restored.setScopeGrants(originalScopes);
            accessGrantRepository.saveAndFlush(restored);
        }
    }

    @Test
    void historicalMixedGuestGrantInvalidatesAnEmployeeSession() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/platform/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequestDto("emp-002", "password"))))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession();

        AccessGrant grant = accessGrantRepository.findById("emp-002").orElseThrow();
        List<String> originalRoles = List.copyOf(grant.getAssignedRoles());
        try {
            grant.setAssignedRoles(List.of("DEVOPS_ADMIN", "GUEST"));
            accessGrantRepository.saveAndFlush(grant);

            mockMvc.perform(get("/api/platform/access-grants").session(session))
                    .andExpect(status().isUnauthorized());
        } finally {
            AccessGrant restored = accessGrantRepository.findById("emp-002").orElseThrow();
            restored.setAssignedRoles(originalRoles);
            accessGrantRepository.saveAndFlush(restored);
        }
    }
}
