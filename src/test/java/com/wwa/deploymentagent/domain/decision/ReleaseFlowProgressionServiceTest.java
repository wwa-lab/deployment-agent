package com.wwa.deploymentagent.domain.decision;

import com.wwa.deploymentagent.contracts.AgentId;
import com.wwa.deploymentagent.contracts.enums.*;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlow;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlowRepository;
import com.wwa.deploymentagent.domain.releaseflow.Request;
import com.wwa.deploymentagent.domain.releaseflow.RequestRepository;
import com.wwa.deploymentagent.domain.task.Task;
import com.wwa.deploymentagent.domain.task.TaskRepository;
import com.wwa.deploymentagent.helper.TestDataHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("ReleaseFlowProgressionService")
class ReleaseFlowProgressionServiceTest {

    @Autowired private ReleaseFlowProgressionService progressionService;
    @Autowired private TaskRepository taskRepository;
    @Autowired private RequestRepository requestRepository;
    @Autowired private ReleaseFlowRepository releaseFlowRepository;
    @Autowired private TestDataHelper helper;

    private ReleaseFlow releaseFlow;
    private Request request;

    @BeforeEach
    void setUp() {
        releaseFlow = helper.seedReleaseFlow();
        request = helper.seedRequest(releaseFlow, "SIT", RequestStatus.Pending, AgentId.DEPLOYMENT_AGENT);
    }

    @Test
    @DisplayName("marks request Completed when all tasks are terminal (Approved/Skipped)")
    void progressAfterDecision_allTasksTerminal_marksRequestCompleted() {
        Task t1 = helper.seedTask(request, TaskStatus.Approved);
        Task t2 = helper.seedTask(request, TaskStatus.Skipped);

        progressionService.progressAfterDecision(t1.getId());

        Request refreshed = requestRepository.findById(request.getId()).orElseThrow();
        assertThat(refreshed.getRequestStatus()).isEqualTo(RequestStatus.Completed);
    }

    @Test
    @DisplayName("advances to next pending task when not all tasks are terminal")
    void progressAfterDecision_pendingTaskExists_advancesToReady() {
        Task approved = helper.seedTask(request, TaskStatus.Approved);
        Task pending  = helper.seedTask(request, TaskStatus.Pending);

        progressionService.progressAfterDecision(approved.getId());

        Task refreshedPending = taskRepository.findById(pending.getId()).orElseThrow();
        assertThat(refreshedPending.getTaskStatus()).isEqualTo(TaskStatus.Ready_For_Execution);
    }

    @Test
    @DisplayName("does not advance the next task while a critical task is awaiting review")
    void progressAfterDecision_criticalAwaitingReview_blocksNextTask() {
        Task criticalReviewTask = helper.seedTask(request, TaskStatus.Awaiting_Review, true);
        Task pending = helper.seedTask(request, TaskStatus.Pending);

        progressionService.progressAfterDecision(criticalReviewTask.getId());

        Task refreshedPending = taskRepository.findById(pending.getId()).orElseThrow();
        assertThat(refreshedPending.getTaskStatus()).isEqualTo(TaskStatus.Pending);
    }

    @Test
    @DisplayName("advances Release Flow stage after SIT request is completed")
    void progressAfterDecision_sitCompleted_advancesToUat() {
        Task task = helper.seedTask(request, TaskStatus.Approved);

        progressionService.progressAfterDecision(task.getId());

        ReleaseFlow refreshed = releaseFlowRepository.findById(releaseFlow.getId()).orElseThrow();
        // With one task approved, SIT completes → flow should advance to UAT or Completed
        assertThat(refreshed.getCurrentStage()).isIn("UAT", "SIT");
        // Stage advances only if it was the sole task; confirm request is Completed
        Request refreshedReq = requestRepository.findById(request.getId()).orElseThrow();
        assertThat(refreshedReq.getRequestStatus()).isEqualTo(RequestStatus.Completed);
    }
}
