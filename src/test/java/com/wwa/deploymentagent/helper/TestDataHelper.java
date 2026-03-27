package com.wwa.deploymentagent.helper;

import com.wwa.deploymentagent.contracts.enums.*;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlow;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlowRepository;
import com.wwa.deploymentagent.domain.releaseflow.Request;
import com.wwa.deploymentagent.domain.releaseflow.RequestRepository;
import com.wwa.deploymentagent.domain.task.Task;
import com.wwa.deploymentagent.domain.task.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Test helper – seeds minimal entities for integration tests.
 * Mirrors the TypeScript seedReleaseFlow / seedRequest / seedTask helpers.
 */
@Component
public class TestDataHelper {

    @Autowired private ReleaseFlowRepository releaseFlowRepository;
    @Autowired private RequestRepository requestRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private jakarta.persistence.EntityManager entityManager;

    @Transactional
    public ReleaseFlow seedReleaseFlow() {
        ReleaseFlow rf = new ReleaseFlow();
        rf.setProjectId("PROJ-001");
        rf.setProjectName("Test Project");
        rf.setReleaseId("SIT-test-project-001");
        rf.setNormalizedReleaseId("sit-test-project-001");
        rf.setCurrentStage(Stage.SIT);
        rf.setFlowStatus(FlowStatus.Pending);
        rf.setReviewStatus(ReviewStatus.Pending_Review);
        return releaseFlowRepository.save(rf);
    }

    @Transactional
    public Request seedRequest(ReleaseFlow releaseFlow) {
        return seedRequest(releaseFlow, Stage.SIT);
    }

    @Transactional
    public Request seedRequest(ReleaseFlow releaseFlow, Stage stage) {
        return seedRequest(releaseFlow, stage, RequestStatus.Pending);
    }

    @Transactional
    public Request seedRequest(ReleaseFlow releaseFlow, Stage stage, RequestStatus status) {
        Request req = new Request();
        req.setReleaseFlow(releaseFlow);
        req.setStage(stage);
        req.setAttemptNumber(requestRepository.findMaxAttemptNumberByReleaseFlowIdAndStage(releaseFlow.getId(), stage) + 1);
        req.setRequestStatus(status);
        Request saved = requestRepository.save(req);
        entityManager.flush();
        entityManager.refresh(releaseFlow);
        return saved;
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
        entityManager.refresh(request);
        return saved;
    }
}
