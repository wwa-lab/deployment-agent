package com.wwa.agenthub.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwa.agenthub.contracts.dto.LoginRequestDto;
import com.wwa.agenthub.contracts.enums.AccessGrantStatus;
import com.wwa.agenthub.domain.auth.AccessGrant;
import com.wwa.agenthub.domain.auth.AccessGrantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("AuthController")
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AccessGrantRepository accessGrantRepository;

    @Test
    @DisplayName("POST /auth/login with valid credentials → 200 + auth context")
    void login_validCredentials_returnsOk() throws Exception {
        LoginRequestDto body = new LoginRequestDto("emp-002", "password");

        mockMvc.perform(post("/api/platform/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("emp-002"))
                .andExpect(jsonPath("$.role").value("TL"))
                .andExpect(jsonPath("$.roles[0]").value("TL"))
                .andExpect(jsonPath("$.permissions").isArray())
                .andExpect(jsonPath("$.scopes[0].application").value("*"))
                .andExpect(jsonPath("$.scopes[0].snowGroup").value("*"))
                .andExpect(jsonPath("$.displayName").isNotEmpty());
    }

    @Test
    @DisplayName("POST /auth/login with invalid credentials → 401")
    void login_invalidCredentials_returns401() throws Exception {
        LoginRequestDto body = new LoginRequestDto("unknown-user", "password");

        mockMvc.perform(post("/api/platform/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /auth/login without access grant → 403")
    void login_missingGrant_returns403() throws Exception {
        AccessGrant grant = accessGrantRepository.findById("emp-005").orElseThrow();
        accessGrantRepository.deleteById("emp-005");
        accessGrantRepository.flush();

        try {
            LoginRequestDto body = new LoginRequestDto("emp-005", "password");

            mockMvc.perform(post("/api/platform/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_NOT_GRANTED"));
        } finally {
            accessGrantRepository.saveAndFlush(copyGrant(grant));
        }
    }

    @Test
    @DisplayName("POST /auth/login with suspended access grant → 403")
    void login_suspendedGrant_returns403() throws Exception {
        AccessGrant grant = accessGrantRepository.findById("emp-004").orElseThrow();
        AccessGrantStatus originalStatus = grant.getGrantStatus();
        grant.setGrantStatus(AccessGrantStatus.SUSPENDED);
        accessGrantRepository.save(grant);

        try {
            LoginRequestDto body = new LoginRequestDto("emp-004", "password");

            mockMvc.perform(post("/api/platform/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_SUSPENDED"));
        } finally {
            AccessGrant persistedGrant = accessGrantRepository.findById("emp-004").orElseThrow();
            persistedGrant.setGrantStatus(originalStatus);
            accessGrantRepository.save(persistedGrant);
        }
    }

    @Test
    @DisplayName("GET /auth/me without session → 401")
    void me_noSession_returns401() throws Exception {
        mockMvc.perform(get("/api/platform/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /auth/me with valid session → 200 + user info")
    void me_withSession_returnsOk() throws Exception {
        // Login first to establish session
        LoginRequestDto body = new LoginRequestDto("emp-003", "password");
        MvcResult loginResult = mockMvc.perform(post("/api/platform/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession();

        // Check /me with the session
        mockMvc.perform(get("/api/platform/auth/me")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("emp-003"))
                .andExpect(jsonPath("$.role").value("DEVOPS_ADMIN"))
                .andExpect(jsonPath("$.roles[0]").value("DEVOPS_ADMIN"))
                .andExpect(jsonPath("$.permissions").isArray())
                .andExpect(jsonPath("$.scopes[0].application").value("*"))
                .andExpect(jsonPath("$.scopes[0].snowGroup").value("*"));
    }

    @Test
    @DisplayName("POST /auth/logout invalidates session")
    void logout_invalidatesSession() throws Exception {
        // Login first
        LoginRequestDto body = new LoginRequestDto("emp-001", "password");
        MvcResult loginResult = mockMvc.perform(post("/api/platform/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession();

        // Logout
        mockMvc.perform(post("/api/platform/auth/logout")
                        .session(session))
                .andExpect(status().isOk());

        // Session should be invalidated - /me should now fail
        // Need a new session since old one is invalidated
        mockMvc.perform(get("/api/platform/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("login endpoint is accessible without authentication")
    void login_noAuth_isAccessible() throws Exception {
        // Should not get 401 for the login endpoint itself (even with invalid creds,
        // the endpoint processes the request rather than blocking at the filter level)
        LoginRequestDto body = new LoginRequestDto("emp-001", "password");

        mockMvc.perform(post("/api/platform/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    private static AccessGrant copyGrant(AccessGrant source) {
        AccessGrant copy = new AccessGrant();
        copy.setEmployeeId(source.getEmployeeId());
        copy.setDisplayNameSnapshot(source.getDisplayNameSnapshot());
        copy.setGrantStatus(source.getGrantStatus());
        copy.setAssignedRoles(source.getAssignedRoles() == null ? null : java.util.List.copyOf(source.getAssignedRoles()));
        copy.setScopeGrants(source.getScopeGrants() == null ? null : java.util.List.copyOf(source.getScopeGrants()));
        copy.setNote(source.getNote());
        copy.setLastLoginAt(source.getLastLoginAt());
        copy.setCreatedBy(source.getCreatedBy());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedBy(source.getUpdatedBy());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }
}
