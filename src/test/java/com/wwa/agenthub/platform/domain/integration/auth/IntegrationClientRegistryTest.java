package com.wwa.agenthub.platform.domain.integration.auth;

import com.wwa.agenthub.contracts.enums.IntegrationClientType;
import com.wwa.agenthub.domain.auth.PermissionResolver;
import com.wwa.agenthub.platform.domain.StagePipelineRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntegrationClientRegistryTest {

    @Test
    void rejectsSyntheticGuestRoleInSoleOrMixedBearerClientRoles() {
        for (List<String> roles : List.of(
                List.of("GUEST"),
                List.of("GUEST", "DEVOPS_ADMIN"))) {
            IntegrationClientProperties properties = new IntegrationClientProperties();
            IntegrationClientProperties.Client client = new IntegrationClientProperties.Client();
            client.setTokenSha256("a".repeat(64));
            client.setApplicationId("client-1");
            client.setClientType(IntegrationClientType.PIPELINE);
            client.setUserId("pipeline-user");
            client.setRoles(roles);
            client.setScopes(List.of("*|*"));
            client.setAllowedAgents(List.of("*"));
            properties.setClients(List.of(client));
            IntegrationClientRegistry registry = new IntegrationClientRegistry(
                    properties,
                    new PermissionResolver(),
                    new StagePipelineRegistry(List.of()),
                    Clock.systemUTC());

            assertThatThrownBy(registry::initialize)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("GUEST");
        }
    }
}
