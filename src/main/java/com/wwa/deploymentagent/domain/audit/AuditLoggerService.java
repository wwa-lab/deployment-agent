package com.wwa.deploymentagent.domain.audit;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.enums.AuditActionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

    private final AuditLogRepository auditLogRepository;

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
            AuditLogEntry entry = new AuditLogEntry();
            entry.setOperatorId(user.userId());
            entry.setOperatorRole(user.role());
            entry.setActionType(actionType);
            entry.setReleaseFlowId(releaseFlowId);
            entry.setRequestId(requestId);
            entry.setTaskId(taskId);
            entry.setContextPayload(context);
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
}
