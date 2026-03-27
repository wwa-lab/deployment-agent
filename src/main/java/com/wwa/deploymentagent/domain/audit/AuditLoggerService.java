package com.wwa.deploymentagent.domain.audit;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.enums.AuditActionType;
import com.wwa.deploymentagent.domain.releaseflow.Request;
import com.wwa.deploymentagent.domain.releaseflow.RequestRepository;
import com.wwa.deploymentagent.domain.task.Task;
import com.wwa.deploymentagent.domain.task.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * AuditLoggerService – centralises audit logging.
 *
 * <p>All audit log writes are append-only (no updates or deletes).
 * An audit failure logs a warning but does NOT abort the calling business operation
 * – audit failures must not corrupt the business flow (design requirement).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLoggerService {

    private final AuditLogRepository auditLogRepository;
    private final RequestRepository requestRepository;
    private final TaskRepository taskRepository;

    /**
     * Appends an audit log entry.
     *
     * <p>Uses {@code REQUIRES_NEW} propagation so that the audit write succeeds or fails
     * independently of the outer transaction. Failures are swallowed with a log warning.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(UserContext user,
                    AuditActionType actionType,
                    String releaseFlowId,
                    String requestId,
                    String taskId,
                    Map<String, Object> context) {
        try {
            ScopeSnapshot scope = resolveScope(context, requestId, taskId);
            AuditLogEntry entry = new AuditLogEntry();
            entry.setOperatorId(user.userId());
            entry.setOperatorRole(user.role());
            entry.setActionType(actionType);
            entry.setReleaseFlowId(releaseFlowId);
            entry.setRequestId(requestId);
            entry.setTaskId(taskId);
            entry.setApplication(scope.application());
            entry.setSnowGroup(scope.snowGroup());
            entry.setAgent(scope.agent());
            // Platform audit standard fields (WWA-009)
            entry.setAgentName("deployment-agent");
            entry.setSourceSystem("wwa-api");
            entry.setContextPayload(enrichContext(context, scope));
            auditLogRepository.save(entry);
        } catch (Exception ex) {
            // Audit failure must not propagate to caller.
            log.warn("[AuditLoggerService] Failed to write audit entry for action={} user={}: {}",
                     actionType, user.userId(), ex.getMessage(), ex);
        }
    }

    /** Convenience overload – no release flow / request / task context. */
    public void log(UserContext user, AuditActionType actionType, Map<String, Object> context) {
        log(user, actionType, null, null, null, context);
    }

    /** Convenience overload – task-scoped action. */
    public void log(UserContext user, AuditActionType actionType,
                    String releaseFlowId, String requestId, String taskId) {
        log(user, actionType, releaseFlowId, requestId, taskId, null);
    }

    private ScopeSnapshot resolveScope(Map<String, Object> context, String requestId, String taskId) {
        ScopeSnapshot fromContext = ScopeSnapshot.from(context);
        ScopeSnapshot resolved = fromContext;

        if (!resolved.isComplete() && taskId != null && !taskId.isBlank()) {
            resolved = resolved.merge(taskRepository.findById(taskId)
                    .map(Task::getRequest)
                    .map(ScopeSnapshot::from)
                    .orElseGet(ScopeSnapshot::empty));
        }

        if (!resolved.isComplete() && requestId != null && !requestId.isBlank()) {
            resolved = resolved.merge(requestRepository.findById(requestId)
                    .map(ScopeSnapshot::from)
                    .orElseGet(ScopeSnapshot::empty));
        }

        return resolved;
    }

    private Map<String, Object> enrichContext(Map<String, Object> context, ScopeSnapshot scope) {
        if (scope.isEmpty()) {
            return context;
        }

        Map<String, Object> enriched = context == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(context);

        if (scope.application() != null) {
            enriched.putIfAbsent("application", scope.application());
        }
        if (scope.snowGroup() != null) {
            enriched.putIfAbsent("snowGroup", scope.snowGroup());
        }
        if (scope.agent() != null) {
            enriched.putIfAbsent("agent", scope.agent());
        }
        return enriched;
    }

    private record ScopeSnapshot(String application, String snowGroup, String agent) {
        static ScopeSnapshot empty() {
            return new ScopeSnapshot(null, null, null);
        }

        static ScopeSnapshot from(Map<String, Object> context) {
            if (context == null || context.isEmpty()) {
                return empty();
            }
            return new ScopeSnapshot(
                    normalizeValue(context.get("application")),
                    normalizeValue(context.get("snowGroup")),
                    normalizeValue(context.get("agent"))
            );
        }

        static ScopeSnapshot from(Request request) {
            return new ScopeSnapshot(
                    normalizeValue(request.getApplication()),
                    normalizeValue(request.getSnowGroup()),
                    normalizeValue(request.getAgent())
            );
        }

        ScopeSnapshot merge(ScopeSnapshot fallback) {
            return new ScopeSnapshot(
                    firstNonBlank(application, fallback.application),
                    firstNonBlank(snowGroup, fallback.snowGroup),
                    firstNonBlank(agent, fallback.agent)
            );
        }

        boolean isComplete() {
            return application != null && snowGroup != null && agent != null;
        }

        boolean isEmpty() {
            return application == null && snowGroup == null && agent == null;
        }

        private static String normalizeValue(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof String text) {
                return normalizeValue(text);
            }
            return normalizeValue(String.valueOf(value));
        }

        private static String normalizeValue(String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }

        private static String firstNonBlank(String primary, String fallback) {
            return primary != null ? primary : fallback;
        }
    }
}
