package com.wwa.agenthub.web;

import com.wwa.agenthub.contracts.enums.AccessGrantStatus;
import com.wwa.agenthub.contracts.enums.AuditActionType;
import com.wwa.agenthub.domain.audit.AuditLogEntry;
import com.wwa.agenthub.domain.audit.AuditLogRepository;
import com.wwa.agenthub.domain.auth.AccessGrant;
import com.wwa.agenthub.domain.auth.AccessGrantRepository;
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

    private static final String BASE = "/api/platform/access-grants";

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
    @DisplayName("GET /access-grants/directory returns Team Book matches for grant creation")
    void directory_returnsSearchResults() throws Exception {
        mockMvc.perform(get(BASE + "/directory")
                        .param("query", "Frank")
                        .header("X-User-Id", "emp-003")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].employeeId").value("emp-006"))
                .andExpect(jsonPath("$[0].displayName").value("Frank Han (Developer)"))
                .andExpect(jsonPath("$[0].hasAccessGrant").value(false))
                .andExpect(jsonPath("$[0].grantStatus").doesNotExist());
    }

    @Test
    @DisplayName("POST /access-grants creates a grant and writes audit")
    void create_createsGrant_andAudits() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "employeeId": "emp-006",
                                  "grantStatus": "ACTIVE",
                                  "assignedRoles": ["AUDIT", "MANAGEMENT"],
                                  "scopeGrants": [
                                    {
                                      "application": "AMH HCC",
                                      "snowGroup": "HTSA-CSI-HCC-AMH-PRJ"
                                    }
                                  ],
                                  "note": "Initial onboarding"
                                }
                                """)
                        .header("X-User-Id", "emp-003")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value("emp-006"))
                .andExpect(jsonPath("$.displayName").value("Frank Han (Developer)"))
                .andExpect(jsonPath("$.grantStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.assignedRoles.length()").value(2))
                .andExpect(jsonPath("$.assignedRoles[0]").value("AUDIT"))
                .andExpect(jsonPath("$.assignedRoles[1]").value("MANAGEMENT"))
                .andExpect(jsonPath("$.scopeGrants.length()").value(1))
                .andExpect(jsonPath("$.scopeGrants[0].application").value("AMH HCC"))
                .andExpect(jsonPath("$.scopeGrants[0].snowGroup").value("HTSA-CSI-HCC-AMH-PRJ"))
                .andExpect(jsonPath("$.updatedBy").value("emp-003"));

        AccessGrant saved = accessGrantRepository.findById("emp-006").orElseThrow();
        assertThat(saved.getAssignedRoles()).containsExactly("AUDIT", "MANAGEMENT");
        assertThat(saved.getScopeGrants()).hasSize(1);

        AuditLogEntry audit = auditLogRepository.findByActionType(
                        AuditActionType.access_grant_create,
                        PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "timestamp")))
                .getContent()
                .getFirst();
        assertThat(audit.getOperatorId()).isEqualTo("emp-003");
        assertThat(audit.getContextPayload()).containsEntry("employeeId", "emp-006");
    }

    @Test
    @DisplayName("POST /access-grants supports manual employee creation with staff ID + display name")
    void create_manualEmployee_succeeds() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "employeeId": "43910156",
                                  "displayName": "Leo L Zhang",
                                  "grantStatus": "ACTIVE",
                                  "assignedRoles": ["DEVELOPER"],
                                  "scopeGrants": [
                                    {
                                      "application": "AMH HCC",
                                      "snowGroup": "HTSA-CSI-HCC-AMH-PRJ"
                                    }
                                  ],
                                  "note": "Manual bootstrap"
                                }
                                """)
                        .header("X-User-Id", "emp-003")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value("43910156"))
                .andExpect(jsonPath("$.displayName").value("Leo L Zhang"))
                .andExpect(jsonPath("$.grantStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.assignedRoles.length()").value(1))
                .andExpect(jsonPath("$.assignedRoles[0]").value("DEVELOPER"));

        AccessGrant saved = accessGrantRepository.findById("43910156").orElseThrow();
        assertThat(saved.getDisplayNameSnapshot()).isEqualTo("Leo L Zhang");
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
