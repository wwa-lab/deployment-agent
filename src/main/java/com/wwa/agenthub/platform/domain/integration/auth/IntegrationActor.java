package com.wwa.agenthub.platform.domain.integration.auth;

import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.enums.IntegrationClientType;

import java.util.Set;

public record IntegrationActor(
        UserContext user,
        String clientApplicationId,
        IntegrationClientType clientType,
        String clientVersion,
        Set<String> allowedAgents,
        boolean bearerAuthenticated
) {
    public IntegrationActor {
        allowedAgents = allowedAgents == null ? Set.of() : Set.copyOf(allowedAgents);
    }

    public String principalId() {
        return user == null ? null : user.userId();
    }

    public boolean allowsAgent(String agentId) {
        return allowedAgents.contains("*") || allowedAgents.contains(agentId);
    }
}
