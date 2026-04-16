package com.wwa.agenthub.contracts.dto;

import com.wwa.agenthub.contracts.enums.AuditActionType;
import com.wwa.agenthub.domain.audit.AuditLogEntry;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record AuditLogEntryDto(
        String id,
        Instant timestamp,
        String operatorId,
        String operatorRole,
        AuditActionType actionType,
        String releaseFlowId,
        String requestId,
        String taskId,
        String application,
        String snowGroup,
        String agent,
        Map<String, Object> contextPayload
) {
    private static final Set<String> SENSITIVE_KEY_MARKERS = Set.of("token", "secret", "password");

    public static AuditLogEntryDto from(AuditLogEntry entry) {
        return new AuditLogEntryDto(
                entry.getId(),
                entry.getTimestamp(),
                entry.getOperatorId(),
                entry.getOperatorRole(),
                entry.getActionType(),
                entry.getReleaseFlowId(),
                entry.getRequestId(),
                entry.getTaskId(),
                entry.getApplication(),
                entry.getSnowGroup(),
                entry.getAgent(),
                sanitizeContext(entry.getActionType(), entry.getContextPayload())
        );
    }

    private static Map<String, Object> sanitizeContext(
            AuditActionType actionType,
            Map<String, Object> contextPayload
    ) {
        if (contextPayload == null || contextPayload.isEmpty()) {
            return contextPayload;
        }

        if (actionType != AuditActionType.config_update) {
            return contextPayload;
        }

        Object configKey = contextPayload.get("configKey");
        if (!(configKey instanceof String key) || !isSensitiveConfigKey(key)) {
            return contextPayload;
        }

        Map<String, Object> sanitized = new LinkedHashMap<>(contextPayload);
        if (sanitized.containsKey("oldValue")) {
            sanitized.put("oldValue", "[REDACTED]");
        }
        if (sanitized.containsKey("newValue")) {
            sanitized.put("newValue", "[REDACTED]");
        }
        return sanitized;
    }

    private static boolean isSensitiveConfigKey(String configKey) {
        String normalized = configKey.toLowerCase();
        return SENSITIVE_KEY_MARKERS.stream().anyMatch(normalized::contains);
    }
}
