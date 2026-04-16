package com.wwa.agenthub.agents.build.web;

import com.wwa.agenthub.contracts.AgentId;
import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.dto.DecisionRequestDto;
import com.wwa.agenthub.contracts.dto.TaskDto;
import com.wwa.agenthub.domain.decision.DecisionEngine;
import com.wwa.agenthub.domain.decision.ReleaseFlowProgressionService;
import com.wwa.agenthub.domain.task.TaskService;
import com.wwa.agenthub.platform.web.security.AgentBoundaryGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Build Agent decision controller (BA-T21).
 *
 * <pre>
 *   POST /api/build-agent/tasks/:id/decision
 * </pre>
 *
 * <p>The task ID is guarded before the decision engine runs. Pipeline resolution
 * happens inside the progression service via {@code StagePipelineRegistry}; since
 * the Build Agent's pipeline is DEV-only and terminal, approving all tasks marks
 * the flow {@code Completed} and does not auto-advance to another stage (SM-08).
 */
@RestController
@RequestMapping("/api/build-agent/tasks")
@RequiredArgsConstructor
public class BuildDecisionController {

    private final DecisionEngine decisionEngine;
    private final ReleaseFlowProgressionService progressionService;
    private final TaskService taskService;
    private final AgentBoundaryGuard boundaryGuard;

    @PostMapping("/{id}/decision")
    public ResponseEntity<TaskDto> applyDecision(
            @PathVariable String id,
            @Valid @RequestBody DecisionRequestDto body,
            @AuthenticationPrincipal UserContext user) {

        boundaryGuard.assertTaskBelongsToAgent(id, AgentId.BUILD_AGENT);
        decisionEngine.applyDecision(id, body.decision(), user, body.comment());
        progressionService.progressAfterDecision(id);

        return ResponseEntity.ok(TaskDto.from(taskService.getById(id)));
    }
}
