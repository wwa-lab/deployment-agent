package com.wwa.deploymentagent.domain.configuration;

import com.wwa.deploymentagent.domain.releaseflow.Request;
import com.wwa.deploymentagent.domain.task.TaskExecutionHistory;

/**
 * Normalized configuration lookup scope used for both stored component rows and
 * runtime resolution requests.
 */
public record ConfigurationScope(
        String application,
        String snowGroup,
        String agent
) {
    private static final String EMPTY_SCOPE_MARKER = "__DEFAULT__";

    public ConfigurationScope {
        application = normalize(application);
        snowGroup = normalize(snowGroup);
        agent = normalize(agent);
    }

    public static ConfigurationScope empty() {
        return new ConfigurationScope(null, null, null);
    }

    public static ConfigurationScope from(Request request) {
        if (request == null) {
            return empty();
        }
        return new ConfigurationScope(
                request.getApplication(),
                request.getSnowGroup(),
                request.getAgent()
        );
    }

    public static ConfigurationScope from(TaskExecutionHistory history) {
        if (history == null) {
            return empty();
        }
        if (history.getConfigApplication() != null
                || history.getConfigSnowGroup() != null
                || history.getConfigAgent() != null) {
            return new ConfigurationScope(
                    history.getConfigApplication(),
                    history.getConfigSnowGroup(),
                    history.getConfigAgent()
            );
        }
        return history.getTask() != null ? from(history.getTask().getRequest()) : empty();
    }

    public String scopeKey() {
        return "APP=" + encode(application)
                + "|SNOW=" + encode(snowGroup)
                + "|AGENT=" + encode(agent);
    }

    public String scopeSource() {
        if (agent != null) {
            return "Agent Override";
        }
        if (snowGroup != null) {
            return "SNOW Group Default";
        }
        if (application != null) {
            return "Application Default";
        }
        return "Platform Default";
    }

    public int specificity() {
        if (agent != null) {
            return 3;
        }
        if (snowGroup != null) {
            return 2;
        }
        if (application != null) {
            return 1;
        }
        return 0;
    }

    public boolean matchesRequestedScope(ConfigurationScope requested) {
        ConfigurationScope target = requested == null ? empty() : requested;
        return matchesPart(application, target.application)
                && matchesPart(snowGroup, target.snowGroup)
                && matchesPart(agent, target.agent);
    }

    public void validateHierarchy() {
        if (snowGroup != null && application == null) {
            throw new IllegalArgumentException("SNOW Group scope requires Application to be set");
        }
        if (agent != null && (application == null || snowGroup == null)) {
            throw new IllegalArgumentException("Agent scope requires both Application and SNOW Group to be set");
        }
    }

    private static boolean matchesPart(String scopedValue, String requestedValue) {
        return scopedValue == null || scopedValue.equalsIgnoreCase(requestedValue);
    }

    private static String encode(String value) {
        return value == null ? EMPTY_SCOPE_MARKER : value;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
