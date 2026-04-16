package com.wwa.agenthub.contracts.dto;

import com.wwa.agenthub.contracts.AccessScope;
import com.wwa.agenthub.contracts.enums.AccessGrantStatus;
import com.wwa.agenthub.domain.auth.AccessGrant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public record AccessGrantDto(
        String employeeId,
        String displayName,
        AccessGrantStatus grantStatus,
        List<String> assignedRoles,
        List<AccessScope> scopeGrants,
        String note,
        Instant lastLoginAt,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt
) {
    public static AccessGrantDto from(AccessGrant grant) {
        return new AccessGrantDto(
                grant.getEmployeeId(),
                grant.getDisplayNameSnapshot(),
                grant.getGrantStatus(),
                List.copyOf(grant.getAssignedRoles()),
                List.copyOf(grant.getScopeGrants()),
                grant.getNote(),
                grant.getLastLoginAt(),
                grant.getCreatedBy(),
                grant.getCreatedAt(),
                grant.getUpdatedBy(),
                grant.getUpdatedAt()
        );
    }

    public record CreateRequest(
            @NotBlank String employeeId,
            String displayName,
            @NotNull AccessGrantStatus grantStatus,
            List<@NotBlank String> assignedRoles,
            List<AccessScope> scopeGrants,
            String note
    ) {}

    public record UpdateRequest(
            List<@NotBlank String> assignedRoles,
            List<AccessScope> scopeGrants,
            String note
    ) {}

    public record SuspendRequest(
            String note
    ) {}

    public record ReactivateRequest(
            List<@NotBlank String> assignedRoles,
            List<AccessScope> scopeGrants,
            String note
    ) {}
}
