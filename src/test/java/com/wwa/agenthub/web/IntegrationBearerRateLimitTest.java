package com.wwa.agenthub.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.integration.authentication-attempt-rate-limit-capacity=2",
        "app.integration.authentication-attempt-rate-limit-refill-per-second=0.01",
        "app.integration.clients[0].token-sha256=c4d19bf88bd6dc3bccd99b9e60887d1f6036d90d7c0d33676c2f067b6c8e5a43",
        "app.integration.clients[0].application-id=atlas-rate-limit-test",
        "app.integration.clients[0].client-type=COPILOT",
        "app.integration.clients[0].user-id=alice",
        "app.integration.clients[0].roles[0]=DEVELOPER",
        "app.integration.clients[0].scopes[0]=*|*",
        "app.integration.clients[0].allowed-agents[0]=deployment-agent"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IntegrationBearerRateLimitTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void repeatedInvalidBearerAttemptsAreThrottledByTrustedRemoteAddress() throws Exception {
        RequestPostProcessor remoteAddress = request -> {
            request.setRemoteAddr("198.51.100.42");
            return request;
        };

        mockMvc.perform(get("/api/v1/integration/tasks")
                        .with(remoteAddress)
                        .header("Authorization", "Bearer invalid-attempt-00000001")
                        .header("X-Forwarded-For", "203.0.113.1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/integration/tasks")
                        .with(remoteAddress)
                        .header("Authorization", "Bearer invalid-attempt-00000002")
                        .header("X-Forwarded-For", "203.0.113.2"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/integration/tasks")
                        .with(remoteAddress)
                        .header("Authorization", "Bearer invalid-attempt-00000003")
                        .header("X-Forwarded-For", "203.0.113.3"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "1"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));

        mockMvc.perform(get("/api/v1/integration/tasks")
                        .with(remoteAddress)
                        .header("Authorization", "Bearer atlas-test-token-1234567890")
                        .header("X-Forwarded-For", "203.0.113.4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
