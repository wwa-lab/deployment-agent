package com.wwa.deploymentagent.web;

import com.wwa.deploymentagent.contracts.enums.AccessGrantStatus;
import com.wwa.deploymentagent.contracts.enums.AuditActionType;
import com.wwa.deploymentagent.domain.audit.AuditLogEntry;
import com.wwa.deploymentagent.domain.audit.AuditLogRepository;
import com.wwa.deploymentagent.domain.auth.AccessGrant;
import com.wwa.deploymentagent.domain.auth.AccessGrantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("AccessGrantController")
class AccessGrantControllerTest {

    private static final String BASE = "/api/deployment-agent/access-grants";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessGrantRepository accessGrantRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    @DisplayName("GET /access-grants requires DEVOPS_ADMIN")
    void list_requiresAdmin() throws Exception {
        mockMvc.perform(get(BASE)
                        .header("X-User-Id", "emp-001")
                        .header("X-User-Role", "DEVELOPER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /access-grants returns paginated response for DEVOPS_ADMIN")
    void list_returnsPaginatedResponse() throws Exception {
        mockMvc.perform(get(BASE)
                        .header("X-User-Id", "emp-003")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.total").isNumber())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    @DisplayName("POST /access-grants creates a grant and writes audit")
    void create_createsGrant_andAudits() throws Exception {
        accessGrantRepository.deleteById("emp-005");

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "employeeId": "emp-005",
                                  "grantStatus": "ACTIVE",
                                  "assignedRoles": ["AUDIT", "MANAGEMENT"],
                                  "note": "Initial onboarding"
                                }
                                """)
                        .header("X-User-Id", "emp-003")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value("emp-005"))
                .andExpect(jsonPath("$.displayName").value("Eve Yoon (Management)"))
                .andExpect(jsonPath("$.grantStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.assignedRoles.length()").value(2))
                .andExpect(jsonPath("$.assignedRoles[0]").value("AUDIT"))
                .andExpect(jsonPath("$.assignedRoles[1]").value("MANAGEMENT"))
                .andExpect(jsonPath("$.updatedBy").value("emp-003"));

        AccessGrant saved = accessGrantRepository.findById("emp-005").orElseThrow();
        assertThat(saved.getAssignedRoles()).containsExactly("AUDIT", "MANAGEMENT");

        AuditLogEntry audit = auditLogRepository.findByActionType(
                        AuditActionType.access_grant_create,
                        PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "timestamp")))
                .getContent()
                .getFirst();
        assertThat(audit.getOperatorId()).isEqualTo("emp-003");
        assertThat(audit.getContextPayload()).containsEntry("employeeId", "emp-005");
    }

    @Test
    @DisplayName("PATCH /access-grants/{employeeId} updates roles and note and writes audit")
    void update_updatesGrant_andAudits() throws Exception {
        mockMvc.perform(patch(BASE + "/emp-004")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assignedRoles": ["AUDIT", "MANAGEMENT"],
                                  "note": "Expanded visibility"
                                }
                                """)
                        .header("X-User-Id", "emp-003")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value("emp-004"))
                .andExpect(jsonPath("$.assignedRoles.length()").value(2))
                .andExpect(jsonPath("$.assignedRoles[0]").value("AUDIT"))
                .andExpect(jsonPath("$.assignedRoles[1]").value("MANAGEMENT"))
                .andExpect(jsonPath("$.note").value("Expanded visibility"));

        AuditLogEntry audit = auditLogRepository.findByActionType(
                        AuditActionType.access_grant_update,
                        PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "timestamp")))
                .getContent()
                .getFirst();
        assertThat(audit.getContextPayload()).containsEntry("employeeId", "emp-004");
    }

    @Test
    @DisplayName("suspend and reactivate endpoints change lifecycle state and write audit")
    void suspendAndReactivate_updatesLifecycle_andAudits() throws Exception {
        mockMvc.perform(post(BASE + "/emp-002/suspend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "note": "Temporary suspension"
                                }
                                """)
                        .header("X-User-Id", "emp-003")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value("emp-002"))
                .andExpect(jsonPath("$.grantStatus").value("SUSPENDED"))
                .andExpect(jsonPath("$.note").value("Temporary suspension"));

        mockMvc.perform(post(BASE + "/emp-002/reactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assignedRoles": ["TL"],
                                  "note": "Returned to active duty"
                                }
                                """)
                        .header("X-User-Id", "emp-003")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value("emp-002"))
                .andExpect(jsonPath("$.grantStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.assignedRoles[0]").value("TL"))
                .andExpect(jsonPath("$.note").value("Returned to active duty"));

        AccessGrant grant = accessGrantRepository.findById("emp-002").orElseThrow();
        assertThat(grant.getGrantStatus()).isEqualTo(AccessGrantStatus.ACTIVE);
        assertThat(grant.getAssignedRoles()).containsExactly("TL");

        AuditLogEntry reactivateAudit = auditLogRepository.findByActionType(
                        AuditActionType.access_grant_reactivate,
                        PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "timestamp")))
                .getContent()
                .getFirst();
        assertThat(reactivateAudit.getContextPayload()).containsEntry("employeeId", "emp-002");
    }

    @Test
    @DisplayName("POST /access-grants/{employeeId}/suspend returns 409 when already suspended")
    void suspend_alreadySuspended_returnsConflict() throws Exception {
        AccessGrant grant = accessGrantRepository.findById("emp-004").orElseThrow();
        grant.setGrantStatus(AccessGrantStatus.SUSPENDED);
        accessGrantRepository.save(grant);

        mockMvc.perform(post(BASE + "/emp-004/suspend")
                        .header("X-User-Id", "emp-003")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }
}
