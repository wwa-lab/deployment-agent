package com.wwa.deploymentagent.domain.decision;

import com.wwa.deploymentagent.contracts.enums.RequestStatus;
import com.wwa.deploymentagent.contracts.enums.TaskStatus;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlow;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlowRepository;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlowService;
import com.wwa.deploymentagent.domain.releaseflow.Request;
import com.wwa.deploymentagent.domain.releaseflow.RequestRepository;
import com.wwa.deploymentagent.domain.task.Task;
import com.wwa.deploymentagent.domain.task.TaskRepository;
import com.wwa.deploymentagent.errors.NotFoundAppException;
import com.wwa.deploymentagent.platform.domain.StagePipeline;
import com.wwa.deploymentagent.platform.domain.StagePipelineRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * ReleaseFlowProgressionService – Orchestrates flow, request, and task progression
 * after decisions are applied.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Check if all tasks in a request are terminal (Approved/Skipped)
 *       → if yes, mark request Completed</li>
 *   <li>If request completed and stage is PROD, mark flow Completed</li>
 *   <li>If request completed and stage &lt; PROD, advance flow to next stage</li>
 *   <li>Find next Pending task in the same request → Ready_For_Execution</li>
 *   <li>Do not release the next task while any critical task is Awaiting_Review</li>
 *   <li>Recompute flow status bottom-up</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class ReleaseFlowProgressionService {

    private final TaskRepository taskRepository;
    private final RequestRepository requestRepository;
    private final ReleaseFlowService releaseFlowService;
    private final ReleaseFlowRepository releaseFlowRepository;
    private final StagePipelineRegistry stagePipelineRegistry;

    /**
     * Progress a release flow after a task decision.
     * Loads the task → request → flow hierarchy and updates state accordingly.
     */
    @Transactional
    public void progressAfterDecision(String taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundAppException("Task", taskId));

        Request request = requestRepository.findById(task.getRequest().getId())
                .orElseThrow(() -> new NotFoundAppException("Request", task.getRequest().getId()));

        ReleaseFlow releaseFlow = releaseFlowRepository.findById(request.getReleaseFlow().getId())
                .orElseThrow(() -> new NotFoundAppException("ReleaseFlow", request.getReleaseFlow().getId()));

        List<Task> requestTasks = taskRepository.findByRequestIdOrderByTaskGroupIdAscStepSeqAsc(request.getId());

        boolean allTasksTerminal = requestTasks.stream()
                .allMatch(t -> t.getTaskStatus() == TaskStatus.Approved
                             || t.getTaskStatus() == TaskStatus.Skipped);

        boolean hasBlockingCriticalReview = requestTasks.stream()
                .anyMatch(t -> t.isCritical() && t.getTaskStatus() == TaskStatus.Awaiting_Review);

        if (allTasksTerminal) {
            request.setRequestStatus(RequestStatus.Completed);
            requestRepository.save(request);

            StagePipeline pipeline = stagePipelineRegistry.forAgent(request.getAgent());
            if (pipeline.isTerminal(releaseFlow.getCurrentStage())) {
                // Terminal stage completed – mark flow as Completed
                releaseFlowService.recomputeAndPersistStatus(releaseFlow.getId());
            } else {
                // Advance to next stage
                releaseFlowService.advanceStage(releaseFlow.getId());
            }
        } else if (!hasBlockingCriticalReview) {
            // Auto-ready the next Pending task
            Optional<Task> nextPending = requestTasks.stream()
                    .filter(t -> t.getTaskStatus() == TaskStatus.Pending)
                    .findFirst();

            nextPending.ifPresent(t -> {
                t.setTaskStatus(TaskStatus.Ready_For_Execution);
                taskRepository.save(t);
            });
        }

        // Recompute flow status bottom-up
        releaseFlowService.recomputeAndPersistStatus(releaseFlow.getId());
    }
}
