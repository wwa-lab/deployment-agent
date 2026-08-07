package com.wwa.agenthub.platform.web.security;

import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.domain.releaseflow.ReleaseFlowRepository;
import com.wwa.agenthub.domain.releaseflow.Request;
import com.wwa.agenthub.domain.releaseflow.RequestRepository;
import com.wwa.agenthub.domain.task.Task;
import com.wwa.agenthub.domain.task.TaskRepository;
import com.wwa.agenthub.errors.NotFoundAppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Controller-layer data isolation guard (architecture PL-9).
 *
 * <p>Each assertion method throws {@link NotFoundAppException} — mapped to HTTP 404 — when
 * the requested resource is not owned by {@code expectedAgent}. Cross-agent probes get the
 * same 404 as missing-id probes, preventing Testing/Build agents from leaking Deployment
 * Agent data through existence timing attacks.
 *
 * <p>This component is introduced in Phase E (BA-T13) as a dormant dependency: no
 * controllers call it yet. Phase H (BA-T19/T20/T21) wires it into the per-agent controllers.
 */
@Component
@RequiredArgsConstructor
public class AgentBoundaryGuard {

    private final TaskRepository taskRepository;
    private final RequestRepository requestRepository;
    private final ReleaseFlowRepository releaseFlowRepository;

    @Transactional(readOnly = true)
    public void assertTaskBelongsToAgent(String taskId, String expectedAgent) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundAppException("Task", taskId));
        Request request = task.getRequest();
        if (request == null || !expectedAgent.equals(request.getAgent())) {
            throw new NotFoundAppException("Task", taskId);
        }
    }

    @Transactional(readOnly = true)
    public void assertRequestBelongsToAgent(String requestId, String expectedAgent) {
        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundAppException("Request", requestId));
        if (!expectedAgent.equals(request.getAgent())) {
            throw new NotFoundAppException("Request", requestId);
        }
    }

    /**
     * Protect legacy Task DTO endpoints from exposing Integration-controlled
     * input/result fields or data outside the caller's Application/SNOW scope.
     */
    @Transactional(readOnly = true)
    public void assertLegacyTaskReadable(String taskId, String expectedAgent, UserContext user) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundAppException("Task", taskId));
        Request request = task.getRequest();
        if (request == null
                || !expectedAgent.equals(request.getAgent())
                || task.isIntegrationBound()
                || user == null
                || !user.hasScopedAccess(request.getApplication(), request.getSnowGroup())) {
            throw new NotFoundAppException("Task", taskId);
        }
    }

    @Transactional(readOnly = true)
    public void assertLegacyRequestReadable(String requestId, String expectedAgent, UserContext user) {
        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundAppException("Request", requestId));
        if (!expectedAgent.equals(request.getAgent())
                || user == null
                || !user.hasScopedAccess(request.getApplication(), request.getSnowGroup())) {
            throw new NotFoundAppException("Request", requestId);
        }
    }

    @Transactional(readOnly = true)
    public void assertFlowBelongsToAgent(String flowId, String expectedAgent) {
        releaseFlowRepository.findById(flowId)
                .orElseThrow(() -> new NotFoundAppException("ReleaseFlow", flowId));
        List<Request> requests = requestRepository.findByReleaseFlowIds(List.of(flowId), true);
        boolean hasAgentRequest = requests.stream()
                .anyMatch(r -> expectedAgent.equals(r.getAgent()));
        if (!hasAgentRequest) {
            throw new NotFoundAppException("ReleaseFlow", flowId);
        }
    }
}
