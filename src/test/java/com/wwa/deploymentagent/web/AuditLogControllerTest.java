package com.wwa.deploymentagent.web;

import com.wwa.deploymentagent.contracts.AccessScope;
import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.enums.AuditActionType;
import com.wwa.deploymentagent.domain.audit.AuditLogEntry;
import com.wwa.deploymentagent.domain.audit.AuditLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.List;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("T13.3 - AuditLogController API contract")
class AuditLogControllerTest {

    private static final String BASE = "/api/platform/audit-logs";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    @DisplayName("list_returnsAuditEntries_forAnyAuthenticatedUser - GET /audit-logs returns 200 with paginated records")
    void list_returnsAuditEntries_forAnyAuthenticatedUser() throws Exception {
        String operatorId = "audit-test-user";
        AuditLogEntry entry = new AuditLogEntry();
        entry.setOperatorId(operatorId);
        entry.setOperatorRole("DEVELOPER");
        entry.setActionType(AuditActionType.request_start);
        entry.setReleaseFlowId("rf-001");
        entry.setRequestId("req-001");
        entry.setTaskId("task-001");
        entry.setApplication("AMH HCC");
        entry.setSnowGroup("HTSA-CSI-HCC-AMH-PRJ");
        entry.setAgent("Deployment Agent");
        entry.setContextPayload(Map.of("stage", "SIT"));
        auditLogRepository.save(entry);

        mockMvc.perform(get(BASE)
                        .param("operatorId", operatorId)
                        .param("application", "AMH HCC")
                        .header("X-User-Id", "emp-001")
                        .header("X-User-Role", "DEVELOPER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].operatorId").value(operatorId))
                .andExpect(jsonPath("$.data[0].actionType").value("request_start"))
                .andExpect(jsonPath("$.data[0].application").value("AMH HCC"))
                .andExpect(jsonPath("$.data[0].snowGroup").value("HTSA-CSI-HCC-AMH-PRJ"))
                .andExpect(jsonPath("$.data[0].agent").value("Deployment Agent"))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    @DisplayName("list_filtersAuditEntries_toCurrentScopedAdminVisibility")
    void list_filtersAuditEntries_toCurrentScopedAdminVisibility() throws Exception {
        AuditLogEntry visible = new AuditLogEntry();
        visible.setOperatorId("scoped-operator");
        visible.setOperatorRole("TL");
        visible.setActionType(AuditActionType.request_start);
        visible.setApplication("AMH HCC");
        visible.setSnowGroup("HTSA-CSI-HCC-AMH-PRJ");
        visible.setAgent("Deployment Agent");
        auditLogRepository.save(visible);

        AuditLogEntry hidden = new AuditLogEntry();
        hidden.setOperatorId("hidden-operator");
        hidden.setOperatorRole("TL");
        hidden.setActionType(AuditActionType.request_start);
        hidden.setApplication("PowerCARD");
        hidden.setSnowGroup("HTSA-CSI-CARD-PRD");
        hidden.setAgent("PowerCARD Agent");
        auditLogRepository.save(hidden);

        UserContext scopedAdmin = new UserContext(
                "emp-admin-001",
                "DEVOPS_ADMIN",
                List.of("DEVOPS_ADMIN"),
                Set.of("access.manage"),
                "Scoped Admin",
                List.of(new AccessScope("AMH HCC", "HTSA-CSI-HCC-AMH-PRJ"))
        );

        mockMvc.perform(get(BASE)
                        .sessionAttr("USER_CONTEXT", scopedAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].operatorId").value("scoped-operator"))
                .andExpect(jsonPath("$.data[0].application").value("AMH HCC"))
                .andExpect(jsonPath("$.data[0].snowGroup").value("HTSA-CSI-HCC-AMH-PRJ"))
                .andExpect(jsonPath("$.total").value(1));
    }
}
