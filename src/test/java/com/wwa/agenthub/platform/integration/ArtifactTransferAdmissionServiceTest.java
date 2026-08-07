package com.wwa.agenthub.platform.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwa.agenthub.platform.domain.integration.artifact.ArtifactTransferAdmissionService;
import com.wwa.agenthub.platform.domain.integration.auth.IntegrationClientProperties;
import com.wwa.agenthub.platform.web.security.ArtifactUploadAdmissionFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactTransferAdmissionServiceTest {

    @Test
    void javaHashCollisionsDoNotShareThePerClientLimit() {
        assertThat("Aa".hashCode()).isEqualTo("BB".hashCode());
        IntegrationClientProperties properties = properties(3, 1);
        ArtifactTransferAdmissionService service = new ArtifactTransferAdmissionService(properties);

        try (var first = service.tryAcquire("Aa");
             var colliding = service.tryAcquire("BB")) {
            assertThat(first).isNotNull();
            assertThat(colliding).isNotNull();
            assertThat(service.tryAcquire("Aa")).isNull();
        }
    }

    @Test
    void multipartIngressIsRejectedBeforeTheFilterChainWhenTransferBudgetIsBusy() throws Exception {
        IntegrationClientProperties properties = properties(1, 1);
        ArtifactTransferAdmissionService service = new ArtifactTransferAdmissionService(properties);
        ArtifactUploadAdmissionFilter filter = new ArtifactUploadAdmissionFilter(service, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/v1/integration/executions/execution-1/artifacts");
        request.setContentType("multipart/form-data; boundary=atlas-boundary");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean();

        try (var ignored = service.tryAcquire("existing-transfer")) {
            filter.doFilter(request, response, (servletRequest, servletResponse) ->
                    chainInvoked.set(true));
        }

        assertThat(chainInvoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).contains("ARTIFACT_UPLOAD_BUSY");
    }

    private static IntegrationClientProperties properties(int global, int perClient) {
        IntegrationClientProperties properties = new IntegrationClientProperties();
        properties.setMaxConcurrentArtifactTransfers(global);
        properties.setMaxConcurrentArtifactTransfersPerClient(perClient);
        return properties;
    }
}
