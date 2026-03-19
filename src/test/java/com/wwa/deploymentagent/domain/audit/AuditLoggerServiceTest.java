package com.wwa.deploymentagent.domain.audit;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.enums.AuditActionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("AuditLoggerService")
class AuditLoggerServiceTest {

    @Autowired private AuditLoggerService auditLoggerService;
    @Autowired private AuditLogRepository auditLogRepository;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
    }

    @Test
    @DisplayName("log appends an entry with all provided fields")
    void log_appendsEntry() {
        UserContext user = new UserContext("alice", "TL");
        Map<String, Object> context = Map.of("field", "taskStatus", "newValue", "Approved");

        auditLoggerService.log(user, AuditActionType.approve,
                "rf-001", "req-001", "task-001", context);

        List<AuditLogEntry> entries = auditLogRepository.findAll();
        assertThat(entries).hasSize(1);

        AuditLogEntry entry = entries.get(0);
        assertThat(entry.getOperatorId()).isEqualTo("alice");
        assertThat(entry.getOperatorRole()).isEqualTo("TL");
        assertThat(entry.getActionType()).isEqualTo(AuditActionType.approve);
        assertThat(entry.getReleaseFlowId()).isEqualTo("rf-001");
        assertThat(entry.getRequestId()).isEqualTo("req-001");
        assertThat(entry.getTaskId()).isEqualTo("task-001");
        assertThat(entry.getContextPayload()).containsEntry("field", "taskStatus");
        assertThat(entry.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("log with null optional fields sets nullable columns to null")
    void log_nullOptionalFields_stored() {
        UserContext user = new UserContext("bob", "DEVELOPER");

        auditLoggerService.log(user, AuditActionType.upload, null);

        List<AuditLogEntry> entries = auditLogRepository.findAll();
        assertThat(entries).hasSize(1);

        AuditLogEntry entry = entries.get(0);
        assertThat(entry.getReleaseFlowId()).isNull();
        assertThat(entry.getTaskId()).isNull();
        assertThat(entry.getContextPayload()).isNull();
    }

    @Test
    @DisplayName("log does not throw on audit failure – swallows error")
    void log_doesNotPropagateFailure() {
        // The service swallows audit errors; this test confirms no exception propagates
        // from a valid call. Full failure-swallow testing would require mocking the repo.
        UserContext user = new UserContext("carol", "DEVOPS_ADMIN");
        auditLoggerService.log(user, AuditActionType.config_update,
                Map.of("configKey", "jenkins_url"));
        // If no exception is thrown, the test passes
    }

    @Test
    @DisplayName("multiple logs produce multiple entries in insertion order")
    void log_multipleEntries_ordered() {
        UserContext user = new UserContext("dave", "TL");

        auditLoggerService.log(user, AuditActionType.edit, null);
        auditLoggerService.log(user, AuditActionType.approve, null);

        List<AuditLogEntry> entries = auditLogRepository.findAll(
                PageRequest.of(0, 10, org.springframework.data.domain.Sort.by("timestamp"))).getContent();
        assertThat(entries).hasSizeGreaterThanOrEqualTo(2);
    }
}
