package com.wwa.deploymentagent.contracts.dto;

import com.wwa.deploymentagent.contracts.enums.AccessGrantStatus;
import com.wwa.deploymentagent.domain.auth.AccessGrantDirectoryCandidate;

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
