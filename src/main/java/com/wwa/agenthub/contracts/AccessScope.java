package com.wwa.agenthub.contracts;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Product scope used for visibility and delegated administration.
 *
 * <p>Current Phase 1 boundary is Application + SNOW Group.
 * Agent remains a runtime execution dimension and is not part of the primary auth scope yet.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccessScope(
        String application,
        String snowGroup
) {
    public static final String WILDCARD = "*";

    public AccessScope {
        application = normalize(application);
        snowGroup = normalize(snowGroup);
    }

    @JsonIgnore
    public boolean isEmpty() {
        return application == null && snowGroup == null;
    }

    @JsonIgnore
    public boolean isWildcard() {
        return WILDCARD.equals(application) || WILDCARD.equals(snowGroup);
    }

    public boolean matches(String targetApplication, String targetSnowGroup) {
        return matchesPart(application, normalize(targetApplication))
                && matchesPart(snowGroup, normalize(targetSnowGroup));
    }

    private static boolean matchesPart(String allowedValue, String targetValue) {
        if (allowedValue == null) {
            return false;
        }
        if (WILDCARD.equals(allowedValue)) {
            return true;
        }
        return allowedValue.equals(targetValue);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
