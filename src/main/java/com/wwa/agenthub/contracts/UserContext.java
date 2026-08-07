package com.wwa.agenthub.contracts;

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
        String displayName,
        List<AccessScope> scopes
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

        List<AccessScope> normalizedScopes = scopes == null ? List.of() : scopes.stream()
                .filter(scope -> scope != null && !scope.isEmpty())
                .distinct()
                .toList();

        role = normalizedRole;
        roles = normalizedRoles;
        permissions = normalizedPermissions;
        displayName = (displayName == null || displayName.isBlank()) ? userId : displayName;
        scopes = normalizedScopes;
    }

    public UserContext(String userId, String role) {
        this(userId, role, role == null || role.isBlank() ? List.of() : List.of(role), Set.of(), userId, List.of());
    }

    public boolean hasRole(String expectedRole) {
        return expectedRole != null && roles.contains(expectedRole);
    }

    public boolean hasPermission(String expectedPermission) {
        return expectedPermission != null && permissions.contains(expectedPermission);
    }

    public boolean isGlobalDevOpsAdmin() {
        return hasRole("DEVOPS_ADMIN") && scopes.isEmpty();
    }

    /**
     * True when the user was created through the anonymous guest login
     * endpoint. Guest viewers bypass scope filtering for read operations
     * so they can browse every page, but write operations are blocked
     * earlier by GuestReadOnlyFilter.
     */
    public boolean isGuestViewer() {
        return roles.size() == 1 && hasRole("GUEST");
    }

    public boolean hasScopedAccess(String application, String snowGroup) {
        if (isGlobalDevOpsAdmin()) {
            return true;
        }
        if (isGuestViewer()) {
            return true;
        }
        if (scopes.isEmpty()) {
            return false;
        }
        return scopes.stream().anyMatch(scope -> scope.matches(application, snowGroup));
    }

    public boolean canManageScopes(List<AccessScope> targetScopes) {
        if (isGlobalDevOpsAdmin()) {
            return true;
        }
        if (!hasRole("DEVOPS_ADMIN")) {
            return false;
        }
        if (targetScopes == null || targetScopes.isEmpty()) {
            return false;
        }
        return targetScopes.stream().allMatch(scope -> hasScopedAccess(scope.application(), scope.snowGroup()));
    }
}
