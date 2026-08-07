package com.wwa.agenthub.helper;

import com.wwa.agenthub.contracts.AgentId;
import com.wwa.agenthub.contracts.AccessScope;
import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.enums.*;
import com.wwa.agenthub.domain.releaseflow.ReleaseFlow;
import com.wwa.agenthub.domain.releaseflow.ReleaseFlowRepository;
import com.wwa.agenthub.domain.releaseflow.Request;
import com.wwa.agenthub.domain.releaseflow.RequestRepository;
import com.wwa.agenthub.domain.task.Task;
import com.wwa.agenthub.domain.task.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Test helper – seeds minimal entities for integration tests.
 * Mirrors the TypeScript seedReleaseFlow / seedRequest / seedTask helpers.
 */
@Component
public class TestDataHelper {

    /**
     * Builds a test principal that can exercise legacy domain workflows without
     * weakening the production rule that non-admin users require an explicit scope.
     */
    public static UserContext globallyScopedUser(String userId, String role) {
        return new UserContext(
                userId,
                role,
                List.of(role),
                Set.of(),
                userId,
                List.of(new AccessScope(AccessScope.WILDCARD, AccessScope.WILDCARD)));
    }

    @Autowired private ReleaseFlowRepository releaseFlowRepository;
    @Autowired private RequestRepository requestRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private jakarta.persistence.EntityManager entityManager;

    @Transactional
    public ReleaseFlow seedReleaseFlow() {
        return seedReleaseFlow("001");
    }

    @Transactional
    public ReleaseFlow seedReleaseFlow(String fixtureId) {
        ReleaseFlow rf = new ReleaseFlow();
        rf.setProjectId("PROJ-" + fixtureId);
        rf.setProjectName("Test Project");
        rf.setReleaseId("SIT-test-project-" + fixtureId);
        rf.setNormalizedReleaseId("sit-test-project-" + fixtureId.toLowerCase(java.util.Locale.ROOT));
        rf.setCurrentStage("SIT");
        rf.setFlowStatus(FlowStatus.Pending);
        rf.setReviewStatus(ReviewStatus.Pending_Review);
        return releaseFlowRepository.save(rf);
    }

    @Transactional
    public Request seedRequest(ReleaseFlow releaseFlow) {
        return seedRequest(releaseFlow, "SIT");
    }

    @Transactional
    public Request seedRequest(ReleaseFlow releaseFlow, String stage) {
        return seedRequest(releaseFlow, stage, RequestStatus.Pending);
    }

    @Transactional
    public Request seedRequest(ReleaseFlow releaseFlow, String stage, RequestStatus status) {
        // Default agent to DEPLOYMENT_AGENT so that audit-aware code paths (BA-T14 null-agent
        // guard) work with legacy test fixtures that do not explicitly set an agent.
        return seedRequest(releaseFlow, stage, status, AgentId.DEPLOYMENT_AGENT);
    }

    @Transactional
    public Request seedRequest(ReleaseFlow releaseFlow, String stage, RequestStatus status, String agent) {
        Request req = new Request();
        req.setReleaseFlow(releaseFlow);
        req.setStage(stage);
        req.setAttemptNumber(requestRepository.findMaxAttemptNumberByReleaseFlowIdAndStage(releaseFlow.getId(), stage) + 1);
        req.setRequestStatus(status);
        req.setAgent(agent);
        Request saved = requestRepository.save(req);
        entityManager.flush();
        if (entityManager.contains(releaseFlow)) {
            entityManager.refresh(releaseFlow);
        }
        return saved;
    }

    @Transactional
    public Request seedRequestWithAgent(ReleaseFlow releaseFlow, String agent) {
        return seedRequest(releaseFlow, "SIT", RequestStatus.Pending, agent);
    }

    @Transactional
    public Task seedTask(Request request) {
        return seedTask(request, TaskStatus.Pending);
    }

    @Transactional
    public Task seedTask(Request request, TaskStatus status) {
        return seedTask(request, status, false);
    }

    @Transactional
    public Task seedTask(Request request, TaskStatus status, boolean critical) {
        Task task = new Task();
        task.setRequest(request);
        task.setTaskGroupId("TG-001");
        task.setTaskGroupName("Deploy App");
        task.setStepSeq(1);
        task.setTaskName("deploy-app");
        task.setExecutionType(ExecutionType.AUTO);
        task.setCritical(critical);
        task.setTaskStatus(status);
        task.setInputParameters(java.util.Map.of("script", "deploy.sh", "parameters", "--env staging"));
        task.setExpectedOutput("Deployment successful");
        task.setOwner("alice");
        Task saved = taskRepository.save(task);
        entityManager.flush();
        if (entityManager.contains(request)) {
            entityManager.refresh(request);
        }
        return saved;
    }

    @Transactional
    public Task seedIntegrationTask(
            Request request,
            TaskStatus status,
            CapabilityType capabilityType,
            String assigneeUserId
    ) {
        request.setApplication("payments");
        request.setSnowGroup("team-atlas");
        requestRepository.save(request);

        Task task = seedTask(request, status, true);
        task.setExecutionType(capabilityType == CapabilityType.MANUAL
                ? ExecutionType.MANUAL
                : ExecutionType.AUTO);
        task.setAssigneeUserId(assigneeUserId);
        task.setOwner(assigneeUserId);
        task.setCapabilityType(capabilityType);
        task.setCapabilityId(capabilityType == CapabilityType.SKILL
                ? "skill.atlas.delivery"
                : "capability.atlas.delivery");
        task.setCapabilityVersion("1.0.0");
        if (capabilityType != CapabilityType.MANUAL) {
            task.setRepositoryId("repo-atlas-001");
            task.setRepositoryProvider("GITHUB");
            task.setRepositoryUrl("https://github.example.invalid/wwa/atlas.git");
            task.setRepositoryBranch("main");
            task.setRepositoryCommit("abcdef1234567890abcdef1234567890abcdef12");
        }
        return taskRepository.save(task);
    }
}
