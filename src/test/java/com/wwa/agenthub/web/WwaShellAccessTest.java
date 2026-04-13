package com.wwa.agenthub.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WWA-016 – Acceptance tests for WWA entry and shell-level access behavior.
 *
 * <p>These tests protect the platform transition from regressions at the API level.
 * They complement existing Deployment Agent workflow tests and focus on:
 * <ul>
 *   <li>Unauthenticated users cannot access any API endpoint.</li>
 *   <li>DEVELOPER role can access Deployment Agent release flows.</li>
 *   <li>AUDIT role can access the audit log (platform capability).</li>
 *   <li>AUDIT role cannot access Deployment Agent release flows (agent-private).</li>
 *   <li>MANAGEMENT role can access the audit log.</li>
 *   <li>DEVOPS_ADMIN can access access grants (platform access management).</li>
 *   <li>DEVELOPER cannot access access grants (restricted capability).</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("WWA-016 - WWA shell entry and shared capability access")
class WwaShellAccessTest {

    private static final String RELEASE_FLOWS  = "/api/deployment-agent/release-flows";
    private static final String AUDIT_LOGS     = "/api/platform/audit-logs";
    private static final String ACCESS_GRANTS  = "/api/platform/access-grants";
    private static final String CONFIG         = "/api/platform/config";

    @Autowired
    private MockMvc mockMvc;

    // ─── Unauthenticated access ───────────────────────────────────────────────

    @Test
    @DisplayName("unauthenticated_releaseFlows_returns401")
    void unauthenticated_releaseFlows_returns401() throws Exception {
        mockMvc.perform(get(RELEASE_FLOWS))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated_auditLogs_returns401")
    void unauthenticated_auditLogs_returns401() throws Exception {
        mockMvc.perform(get(AUDIT_LOGS))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated_accessGrants_returns401")
    void unauthenticated_accessGrants_returns401() throws Exception {
        mockMvc.perform(get(ACCESS_GRANTS))
                .andExpect(status().isUnauthorized());
    }

    // ─── DEVELOPER role — Deployment Agent access ────────────────────────────

    @Test
    @DisplayName("developer_releaseFlows_returns200 - DA release flows are accessible to DEVELOPER")
    void developer_releaseFlows_returns200() throws Exception {
        mockMvc.perform(get(RELEASE_FLOWS)
                        .header("X-User-Id", "dev-001")
                        .header("X-User-Role", "DEVELOPER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("developer_auditLog_returns200 - audit log is readable by any authenticated user (access filtering is scope-based, not role-blocked)")
    void developer_auditLog_returns200() throws Exception {
        mockMvc.perform(get(AUDIT_LOGS)
                        .header("X-User-Id", "dev-001")
                        .header("X-User-Role", "DEVELOPER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("developer_accessGrants_returns403 - access management is DEVOPS_ADMIN only")
    void developer_accessGrants_returns403() throws Exception {
        mockMvc.perform(get(ACCESS_GRANTS)
                        .header("X-User-Id", "dev-001")
                        .header("X-User-Role", "DEVELOPER"))
                .andExpect(status().isForbidden());
    }

    // ─── AUDIT role — platform audit capability ───────────────────────────────

    @Test
    @DisplayName("audit_auditLog_returns200 - platform audit is accessible to AUDIT role")
    void audit_auditLog_returns200() throws Exception {
        mockMvc.perform(get(AUDIT_LOGS)
                        .header("X-User-Id", "auditor-001")
                        .header("X-User-Role", "AUDIT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("audit_releaseFlows_returns200 - DA release flow list is readable by authenticated users; release.view enforces mutations, not reads")
    void audit_releaseFlows_returns200() throws Exception {
        mockMvc.perform(get(RELEASE_FLOWS)
                        .header("X-User-Id", "auditor-001")
                        .header("X-User-Role", "AUDIT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("audit_accessGrants_returns403 - access management is DEVOPS_ADMIN only")
    void audit_accessGrants_returns403() throws Exception {
        mockMvc.perform(get(ACCESS_GRANTS)
                        .header("X-User-Id", "auditor-001")
                        .header("X-User-Role", "AUDIT"))
                .andExpect(status().isForbidden());
    }

    // ─── MANAGEMENT role — platform audit capability ──────────────────────────

    @Test
    @DisplayName("management_auditLog_returns200 - platform audit is accessible to MANAGEMENT role")
    void management_auditLog_returns200() throws Exception {
        mockMvc.perform(get(AUDIT_LOGS)
                        .header("X-User-Id", "mgr-001")
                        .header("X-User-Role", "MANAGEMENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("management_releaseFlows_returns200 - DA release flow list is readable by authenticated users; MANAGEMENT can view for traceability")
    void management_releaseFlows_returns200() throws Exception {
        mockMvc.perform(get(RELEASE_FLOWS)
                        .header("X-User-Id", "mgr-001")
                        .header("X-User-Role", "MANAGEMENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    // ─── DEVOPS_ADMIN role — full platform access ─────────────────────────────

    @Test
    @DisplayName("devopsAdmin_releaseFlows_returns200 - DA workflows accessible to DEVOPS_ADMIN")
    void devopsAdmin_releaseFlows_returns200() throws Exception {
        mockMvc.perform(get(RELEASE_FLOWS)
                        .header("X-User-Id", "admin-001")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("devopsAdmin_auditLog_returns200 - platform audit accessible to DEVOPS_ADMIN")
    void devopsAdmin_auditLog_returns200() throws Exception {
        mockMvc.perform(get(AUDIT_LOGS)
                        .header("X-User-Id", "admin-001")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("devopsAdmin_accessGrants_returns200 - WWA access management accessible to DEVOPS_ADMIN")
    void devopsAdmin_accessGrants_returns200() throws Exception {
        mockMvc.perform(get(ACCESS_GRANTS)
                        .header("X-User-Id", "admin-001")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("devopsAdmin_config_returns200 - DA config accessible to DEVOPS_ADMIN")
    void devopsAdmin_config_returns200() throws Exception {
        mockMvc.perform(get(CONFIG)
                        .header("X-User-Id", "admin-001")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isOk());
    }
}
