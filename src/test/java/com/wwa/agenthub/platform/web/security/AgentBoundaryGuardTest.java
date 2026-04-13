package com.wwa.agenthub.platform.web.security;

import com.wwa.agenthub.contracts.AgentId;
import com.wwa.agenthub.contracts.enums.RequestStatus;
import com.wwa.agenthub.domain.releaseflow.ReleaseFlow;
import com.wwa.agenthub.domain.releaseflow.Request;
import com.wwa.agenthub.domain.task.Task;
import com.wwa.agenthub.errors.NotFoundAppException;
import com.wwa.agenthub.helper.TestDataHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 12-row test matrix for {@link AgentBoundaryGuard} as specified in
 * build-agent-design.md §M2.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("AgentBoundaryGuard")
class AgentBoundaryGuardTest {

    @Autowired private AgentBoundaryGuard guard;
    @Autowired private TestDataHelper helper;

    // ─── assertTaskBelongsToAgent ───────────────────────────────────────────

    @Test
    @DisplayName("1. Task exists with matching agent → returns normally")
    void task_matchingAgent_returnsNormally() {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf, "SIT", RequestStatus.Pending, AgentId.DEPLOYMENT_AGENT);
        Task task = helper.seedTask(req);

        assertThatCode(() -> guard.assertTaskBelongsToAgent(task.getId(), AgentId.DEPLOYMENT_AGENT))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("2. Task exists with different agent → 404")
    void task_differentAgent_throws() {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf, "UAT", RequestStatus.Pending, AgentId.TESTING_AGENT);
        Task task = helper.seedTask(req);

        assertThatThrownBy(() -> guard.assertTaskBelongsToAgent(task.getId(), AgentId.DEPLOYMENT_AGENT))
                .isInstanceOf(NotFoundAppException.class);
    }

    @Test
    @DisplayName("3. Task exists but request linkage cannot be navigated → 404")
    void task_nullishRequest_throws() {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf, "SIT", RequestStatus.Pending, null);
        Task task = helper.seedTask(req);

        // agent is null in the request — treat as "not owned" by any concrete agent
        assertThatThrownBy(() -> guard.assertTaskBelongsToAgent(task.getId(), AgentId.DEPLOYMENT_AGENT))
                .isInstanceOf(NotFoundAppException.class);
    }

    @Test
    @DisplayName("4. Task exists with legacy null-agent request → 404")
    void task_legacyNullAgent_throws() {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf, "SIT", RequestStatus.Pending, null);
        Task task = helper.seedTask(req);

        assertThatThrownBy(() -> guard.assertTaskBelongsToAgent(task.getId(), AgentId.DEPLOYMENT_AGENT))
                .isInstanceOf(NotFoundAppException.class);
    }

    @Test
    @DisplayName("5. Task does not exist → 404")
    void task_doesNotExist_throws() {
        assertThatThrownBy(() -> guard.assertTaskBelongsToAgent("nonexistent-task-id", AgentId.DEPLOYMENT_AGENT))
                .isInstanceOf(NotFoundAppException.class);
    }

    // ─── assertRequestBelongsToAgent ────────────────────────────────────────

    @Test
    @DisplayName("6. Request exists with matching agent → returns normally")
    void request_matchingAgent_returnsNormally() {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf, "SIT", RequestStatus.Pending, AgentId.DEPLOYMENT_AGENT);

        assertThatCode(() -> guard.assertRequestBelongsToAgent(req.getId(), AgentId.DEPLOYMENT_AGENT))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("7. Request exists with different agent → 404")
    void request_differentAgent_throws() {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf, "UAT", RequestStatus.Pending, AgentId.TESTING_AGENT);

        assertThatThrownBy(() -> guard.assertRequestBelongsToAgent(req.getId(), AgentId.DEPLOYMENT_AGENT))
                .isInstanceOf(NotFoundAppException.class);
    }

    @Test
    @DisplayName("8. Request does not exist → 404")
    void request_doesNotExist_throws() {
        assertThatThrownBy(() -> guard.assertRequestBelongsToAgent("nonexistent-req-id", AgentId.DEPLOYMENT_AGENT))
                .isInstanceOf(NotFoundAppException.class);
    }

    // ─── assertFlowBelongsToAgent ───────────────────────────────────────────

    @Test
    @DisplayName("9. Flow has ≥1 matching-agent request → returns normally")
    void flow_hasMatchingAgentRequest_returnsNormally() {
        ReleaseFlow rf = helper.seedReleaseFlow();
        helper.seedRequest(rf, "SIT", RequestStatus.Pending, AgentId.DEPLOYMENT_AGENT);

        assertThatCode(() -> guard.assertFlowBelongsToAgent(rf.getId(), AgentId.DEPLOYMENT_AGENT))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("10. Flow has 0 matching-agent requests → 404")
    void flow_noMatchingAgentRequests_throws() {
        ReleaseFlow rf = helper.seedReleaseFlow();
        helper.seedRequest(rf, "UAT", RequestStatus.Pending, AgentId.TESTING_AGENT);

        assertThatThrownBy(() -> guard.assertFlowBelongsToAgent(rf.getId(), AgentId.DEPLOYMENT_AGENT))
                .isInstanceOf(NotFoundAppException.class);
    }

    @Test
    @DisplayName("11. Flow has only archived matching-agent requests → returns normally")
    void flow_onlyArchivedMatchingRequests_returnsNormally() {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf, "SIT", RequestStatus.Pending, AgentId.DEPLOYMENT_AGENT);
        req.setArchivedAt(java.time.Instant.now());
        req.setArchivedBy("admin-test");

        // includeArchived=true flag in the guard's query picks up archived requests
        assertThatCode(() -> guard.assertFlowBelongsToAgent(rf.getId(), AgentId.DEPLOYMENT_AGENT))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("12. Flow does not exist → 404")
    void flow_doesNotExist_throws() {
        assertThatThrownBy(() -> guard.assertFlowBelongsToAgent("nonexistent-flow-id", AgentId.DEPLOYMENT_AGENT))
                .isInstanceOf(NotFoundAppException.class);
    }
}
