package com.wwa.agenthub.platform.domain.integration.artifact;

import com.wwa.agenthub.platform.domain.integration.IntegrationApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Serializes the memory-intensive scan/persist stage for one exact Execution.
 * Global/client ingress admission is held by ArtifactUploadAdmissionFilter
 * before multipart parsing; database row locks remain the durable quota fence.
 */
@Service
public class ArtifactUploadAdmissionService {

    private final KeyedSemaphoreAdmission executions = new KeyedSemaphoreAdmission(1);

    public ArtifactUploadAdmissionService() {
    }

    public <T> T execute(String executionId, Supplier<T> command) {
        KeyedSemaphoreAdmission.Permit execution = executions.tryAcquire(executionId);
        if (execution == null) {
            throw busy();
        }
        try (execution) {
            return command.get();
        }
    }

    private static IntegrationApiException busy() {
        return new IntegrationApiException(
                HttpStatus.TOO_MANY_REQUESTS,
                "ARTIFACT_UPLOAD_BUSY",
                "Artifact upload capacity is busy. Retry with the same idempotency key.",
                true);
    }
}
