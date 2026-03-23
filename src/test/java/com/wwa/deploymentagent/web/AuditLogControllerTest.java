package com.wwa.deploymentagent.web;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("T13.3 - AuditLogController API contract")
class AuditLogControllerTest {

    private static final String BASE = "/api/deployment-agent/audit-logs";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    @DisplayName("list_returnsAuditEntries_forAnyAuthenticatedUser - GET /audit-logs returns 200 with paginated records")
    void list_returnsAuditEntries_forAnyAuthenticatedUser() throws Exception {
        AuditLogEntry entry = new AuditLogEntry();
        entry.setOperatorId("emp-001");
        entry.setOperatorRole("DEVELOPER");
        entry.setActionType(AuditActionType.request_start);
        entry.setReleaseFlowId("rf-001");
        entry.setRequestId("req-001");
        entry.setTaskId("task-001");
        entry.setContextPayload(Map.of("stage", "SIT"));
        auditLogRepository.save(entry);

        mockMvc.perform(get(BASE)
                        .header("X-User-Id", "emp-001")
                        .header("X-User-Role", "DEVELOPER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].operatorId").value("emp-001"))
                .andExpect(jsonPath("$.data[0].actionType").value("request_start"))
                .andExpect(jsonPath("$.total").value(1));
    }
}
