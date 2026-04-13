package com.wwa.agenthub.contracts.dto;

import com.wwa.agenthub.contracts.enums.AccessGrantStatus;
import com.wwa.agenthub.domain.auth.AccessGrantDirectoryCandidate;

public record AccessGrantDirectoryCandidateDto(
        String employeeId,
        String displayName,
        boolean hasAccessGrant,
        AccessGrantStatus grantStatus
) {
    public static AccessGrantDirectoryCandidateDto from(AccessGrantDirectoryCandidate candidate) {
        return new AccessGrantDirectoryCandidateDto(
                candidate.employeeId(),
                candidate.displayName(),
                candidate.hasAccessGrant(),
                candidate.grantStatus()
        );
    }
}
