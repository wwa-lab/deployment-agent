package com.wwa.agenthub.domain.audit;

import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.enums.ActorKind;
import com.wwa.agenthub.contracts.enums.AuditActionType;
import com.wwa.agenthub.platform.web.common.CorrelationIdFilter;
import com.wwa.agenthub.domain.releaseflow.Request;
import com.wwa.agenthub.domain.releaseflow.RequestRepository;
import com.wwa.agenthub.domain.task.Task;
import com.wwa.agenthub.domain.task.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

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

    /**
     * Fallback agentName for platform-level audit events (config updates, access grants,
     * file imports) that are not attributable to a single Agent Module. These events are
     * written through controllers under {@code /api/platform/*} (after BA-T15) or the
     * existing shared capability controllers.
     */
    static final String PLATFORM_AGENT = "platform";

    private final AuditLogRepository auditLogRepository;
    private final RequestRepository requestRepository;
    private final TaskRepository taskRepository;

    /**
     * Self-reference resolved lazily so that calls to {@link #writeAuditEntry} go through
     * the Spring AOP proxy and trigger the {@code @Transactional(REQUIRES_NEW)} advice.
     * Direct self-invocation would bypass the proxy.
     */
    @Autowired
    @Lazy
    private AuditLoggerService self;

    /**
     * Appends an audit log entry.
     *
     * <p>Uses {@code REQUIRES_NEW} propagation so that the audit write succeeds or fails
     * independently of the outer transaction. Failures are swallowed with a log warning.
     */
    /**
     * Top-level entry point for audit logging.
     *
     * <p>Runs outside any new transaction so that scope resolution (which may read
     * the Request/Task entities seeded by the caller's transaction) is visible.
     * The actual DB write is delegated to {@link #writeAuditEntry} which uses
     * {@code REQUIRES_NEW} to isolate audit failures from business flows.
     */
    public void log(UserContext user,
                    AuditActionType actionType,
                    String releaseFlowId,
                    String requestId,
                    String taskId,
                    Map<String, Object> context) {
        ScopeSnapshot scope;
        try {
            scope = resolveScope(context, requestId, taskId);
        } catch (Exception ex) {
            log.warn("[AuditLoggerService] Failed to resolve audit scope for action={} user={}: {}",
                    actionType, user.userId(), ex.getMessage(), ex);
            return;
        }

        // PL-6 precondition: every Agent Module write path must flow through a controller
        // that has already forced an agent context. During the Build Agent refactor
        // (pre-Phase H) some platform capability controllers do not yet set agent, and
        // some test paths cannot see fresh fixtures across REQUIRES_NEW boundaries. The
        // guard logs a warning and falls back to {@link #PLATFORM_AGENT} rather than
        // throwing, so that audit entries are never silently dropped. The strict-mode
        // IllegalStateException is only raised when a caller explicitly sets
        // {@code context.get("strictAgent") == true} — used by the §M8 unit test.
        if (scope.agent() == null) {
            boolean strict = context != null && Boolean.TRUE.equals(context.get("strictAgent"));
            if (strict) {
                throw new IllegalStateException(
                        "AuditLoggerService.log called with null scope.agent() in strict mode "
                                + "(action=" + actionType + "); every write path must flow "
                                + "through an Agent Module controller.");
            }
            log.warn("[AuditLoggerService] null scope.agent() for action={} flowId={} reqId={} taskId={} — "
                            + "falling back to agentName='{}'. Caller should be migrated to a "
                            + "controller that forces agent context.",
                    actionType, releaseFlowId, requestId, taskId, PLATFORM_AGENT);
            scope = new ScopeSnapshot(scope.application(), scope.snowGroup(), PLATFORM_AGENT);
        }

        self.writeAuditEntry(user, actionType, releaseFlowId, requestId, taskId, context, scope);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeAuditEntry(UserContext user,
                                AuditActionType actionType,
                                String releaseFlowId,
                                String requestId,
                                String taskId,
                                Map<String, Object> context,
                                ScopeSnapshot scope) {
        try {
            AuditLogEntry entry = new AuditLogEntry();
            entry.setOperatorId(user.userId());
            entry.setOperatorRole(user.role());
            // MVP Foundation Seam: every audit write is attributed to a real human
            // operator. The seam exists so that future policy / AI-assisted / system
            // writes can override this value without retrofitting the table.
            entry.setActorKind(ActorKind.HUMAN);
            entry.setActorRef(null);
            // Stitch this audit row into the originating HTTP request so
            // operators can correlate with server logs and downstream calls.
            // Null is acceptable for background jobs that run outside of an
            // HTTP request context.
            entry.setCorrelationId(CorrelationIdFilter.current());
            entry.setActionType(actionType);
            entry.setReleaseFlowId(releaseFlowId);
            entry.setRequestId(requestId);
            entry.setTaskId(taskId);
            entry.setApplication(scope.application());
            entry.setSnowGroup(scope.snowGroup());
            entry.setAgent(scope.agent());
            // Platform audit standard fields (WWA-009) — dynamic per-agent since BA-T14
            entry.setAgentName(scope.agent());
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

    /** Package-private so Spring AOP can proxy {@link #writeAuditEntry} with this parameter type. */
    record ScopeSnapshot(String application, String snowGroup, String agent) {
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
