package com.wwa.deploymentagent.domain.auth;

/**
 * Employee record returned from the team book authentication provider.
 *
 * @param employeeId  unique employee identifier (e.g. emp-001)
 * @param displayName human-readable name
 * @param role        RBAC role: DEVELOPER, TL, DEVOPS_ADMIN, AUDIT, MANAGEMENT
 */
public record TeamBookEmployee(
        String employeeId,
        String displayName,
        String role
) {}
