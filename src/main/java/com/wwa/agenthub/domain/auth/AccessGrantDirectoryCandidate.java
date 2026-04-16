package com.wwa.agenthub.domain.auth;

import com.wwa.agenthub.contracts.enums.AccessGrantStatus;

public record AccessGrantDirectoryCandidate(
        String employeeId,
        String displayName,
        boolean hasAccessGrant,
        AccessGrantStatus grantStatus
) {}
