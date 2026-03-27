package com.wwa.deploymentagent.domain.auth;

import com.wwa.deploymentagent.contracts.enums.AccessGrantStatus;

public record AccessGrantDirectoryCandidate(
        String employeeId,
        String displayName,
        boolean hasAccessGrant,
        AccessGrantStatus grantStatus
) {}
