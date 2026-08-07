package com.wwa.agenthub.platform.domain.integration.auth;

import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.enums.IntegrationClientType;

import java.time.Instant;
import java.util.Set;

public record IntegrationClientDescriptor(
        byte[] tokenDigest,
        String applicationId,
        IntegrationClientType clientType,
        String clientVersion,
        UserContext user,
        Set<String> allowedAgents,
        Instant expiresAt
) {
    public IntegrationClientDescriptor {
        tokenDigest = tokenDigest.clone();
        allowedAgents = Set.copyOf(allowedAgents);
    }

    @Override
    public byte[] tokenDigest() {
        return tokenDigest.clone();
    }
}
