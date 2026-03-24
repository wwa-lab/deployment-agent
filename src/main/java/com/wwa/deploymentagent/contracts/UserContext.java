package com.wwa.deploymentagent.contracts;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Authenticated user context stored in session and propagated through Spring Security.
 */
public record UserContext(
        String userId,
        String role,
        List<String> roles,
        Set<String> permissions,
        String displayName
) {

    public UserContext {
        String normalizedRole = role == null ? null : role.trim();
        List<String> normalizedRoles = roles == null ? List.of() : roles.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (normalizedRoles.isEmpty() && normalizedRole != null && !normalizedRole.isBlank()) {
            normalizedRoles = List.of(normalizedRole);
        }
        if ((normalizedRole == null || normalizedRole.isBlank()) && !normalizedRoles.isEmpty()) {
            normalizedRole = normalizedRoles.get(0);
        }

        Set<String> normalizedPermissions = permissions == null ? Set.of() : Set.copyOf(
                new LinkedHashSet<>(permissions.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(String::trim)
                        .toList())
        );

        role = normalizedRole;
        roles = normalizedRoles;
        permissions = normalizedPermissions;
        displayName = (displayName == null || displayName.isBlank()) ? userId : displayName;
    }

    public UserContext(String userId, String role) {
        this(userId, role, role == null || role.isBlank() ? List.of() : List.of(role), Set.of(), userId);
    }

    public boolean hasRole(String expectedRole) {
        return expectedRole != null && roles.contains(expectedRole);
    }

    public boolean hasPermission(String expectedPermission) {
        return expectedPermission != null && permissions.contains(expectedPermission);
    }
}
