package com.wwa.agenthub.agents.deployment.web;

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
 * Deployment Agent decision controller (BA-T19).
 *
 * <pre>
 *   POST /api/deployment-agent/tasks/:id/decision
 * </pre>
 *
 * <p>The task ID is guarded via {@link AgentBoundaryGuard} before the decision engine
 * runs, so cross-agent probes receive 404 rather than modifying state (PL-9).
 * Stage progression is delegated to {@link ReleaseFlowProgressionService}, which
 * resolves the per-agent pipeline through {@code StagePipelineRegistry}.
 */
@RestController
@RequestMapping("/api/deployment-agent/tasks")
@RequiredArgsConstructor
public class DeploymentDecisionController {

    private final DecisionEngine decisionEngine;
    private final ReleaseFlowProgressionService progressionService;
    private final TaskService taskService;
    private final AgentBoundaryGuard boundaryGuard;

    @PostMapping("/{id}/decision")
    public ResponseEntity<TaskDto> applyDecision(
            @PathVariable String id,
            @Valid @RequestBody DecisionRequestDto body,
            @AuthenticationPrincipal UserContext user) {

        boundaryGuard.assertTaskBelongsToAgent(id, AgentId.DEPLOYMENT_AGENT);
        decisionEngine.applyDecision(id, body.decision(), user, body.comment());
        progressionService.progressAfterDecision(id);

        return ResponseEntity.ok(TaskDto.from(taskService.getById(id)));
    }
}
