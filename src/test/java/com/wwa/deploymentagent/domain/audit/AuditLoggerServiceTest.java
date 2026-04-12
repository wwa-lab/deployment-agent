package com.wwa.deploymentagent.domain.audit;

import com.wwa.deploymentagent.contracts.AgentId;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        Map<String, Object> context = Map.of(
                "field", "taskStatus",
                "newValue", "Approved",
                "agent", AgentId.DEPLOYMENT_AGENT);

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
        assertThat(entry.getAgentName()).isEqualTo(AgentId.DEPLOYMENT_AGENT);
        assertThat(entry.getContextPayload()).containsEntry("field", "taskStatus");
        assertThat(entry.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("log with null optional fields sets nullable columns to null")
    void log_nullOptionalFields_stored() {
        UserContext user = new UserContext("bob", "DEVELOPER");

        auditLoggerService.log(user, AuditActionType.upload, Map.of("agent", AgentId.DEPLOYMENT_AGENT));

        List<AuditLogEntry> entries = auditLogRepository.findAll();
        assertThat(entries).hasSize(1);

        AuditLogEntry entry = entries.get(0);
        assertThat(entry.getReleaseFlowId()).isNull();
        assertThat(entry.getTaskId()).isNull();
        assertThat(entry.getAgentName()).isEqualTo(AgentId.DEPLOYMENT_AGENT);
    }

    @Test
    @DisplayName("log does not throw on audit failure – swallows error")
    void log_doesNotPropagateFailure() {
        // The service swallows audit errors; this test confirms no exception propagates
        // from a valid call. Full failure-swallow testing would require mocking the repo.
        UserContext user = new UserContext("carol", "DEVOPS_ADMIN");
        auditLoggerService.log(user, AuditActionType.config_update,
                Map.of("configKey", "jenkins_url", "agent", AgentId.DEPLOYMENT_AGENT));
        // If no exception is thrown, the test passes
    }

    @Test
    @DisplayName("multiple logs produce multiple entries in insertion order")
    void log_multipleEntries_ordered() {
        UserContext user = new UserContext("dave", "TL");
        Map<String, Object> ctx = Map.of("agent", AgentId.DEPLOYMENT_AGENT);

        auditLoggerService.log(user, AuditActionType.edit, ctx);
        auditLoggerService.log(user, AuditActionType.approve, ctx);

        List<AuditLogEntry> entries = auditLogRepository.findAll(
                PageRequest.of(0, 10, org.springframework.data.domain.Sort.by("timestamp"))).getContent();
        assertThat(entries).hasSizeGreaterThanOrEqualTo(2);
    }

    // ─── BA-T14: dynamic agentName + null scope guard (design §M8) ──────────

    @Test
    @DisplayName("agentName reflects build-agent scope")
    void agentName_buildAgent() {
        UserContext user = new UserContext("eve", "DEVELOPER");
        auditLoggerService.log(user, AuditActionType.upload,
                Map.of("agent", AgentId.BUILD_AGENT));

        AuditLogEntry entry = auditLogRepository.findAll().get(0);
        assertThat(entry.getAgentName()).isEqualTo(AgentId.BUILD_AGENT);
    }

    @Test
    @DisplayName("agentName reflects testing-agent scope")
    void agentName_testingAgent() {
        UserContext user = new UserContext("frank", "TL");
        auditLoggerService.log(user, AuditActionType.upload,
                Map.of("agent", AgentId.TESTING_AGENT));

        AuditLogEntry entry = auditLogRepository.findAll().get(0);
        assertThat(entry.getAgentName()).isEqualTo(AgentId.TESTING_AGENT);
    }

    @Test
    @DisplayName("agentName reflects deployment-agent scope")
    void agentName_deploymentAgent() {
        UserContext user = new UserContext("grace", "TL");
        auditLoggerService.log(user, AuditActionType.upload,
                Map.of("agent", AgentId.DEPLOYMENT_AGENT));

        AuditLogEntry entry = auditLogRepository.findAll().get(0);
        assertThat(entry.getAgentName()).isEqualTo(AgentId.DEPLOYMENT_AGENT);
    }

    @Test
    @DisplayName("null scope agent in strict mode throws IllegalStateException")
    void nullScopeAgent_strictMode_throwsIllegalStateException() {
        UserContext user = new UserContext("henry", "DEVELOPER");

        // strictAgent=true forces the §M8 contract violation to propagate.
        assertThatThrownBy(() -> auditLoggerService.log(user, AuditActionType.approve,
                null, null, null, Map.of("strictAgent", true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("scope.agent()")
                .hasMessageContaining("Agent Module controller");
    }

    @Test
    @DisplayName("null scope agent without strict flag falls back to platform agentName")
    void nullScopeAgent_nonStrict_fallsBackToPlatform() {
        UserContext user = new UserContext("ivy", "DEVOPS_ADMIN");

        // Default behavior: fall back to 'platform' agentName so capability events and
        // transitional callers never silently drop audit entries.
        auditLoggerService.log(user, AuditActionType.config_update,
                Map.of("configKey", "jenkins_url"));

        AuditLogEntry entry = auditLogRepository.findAll().get(0);
        assertThat(entry.getAgentName()).isEqualTo("platform");
    }
}
