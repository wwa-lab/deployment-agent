package com.wwa.agenthub.platform.domain.integration.artifact;

import com.wwa.agenthub.platform.domain.integration.auth.IntegrationClientProperties;
import org.springframework.stereotype.Service;

import java.util.concurrent.Semaphore;

/**
 * One node-wide budget shared by Artifact uploads and downloads. Holding the
 * permit across persistence/response serialization bounds their combined
 * in-memory pressure instead of allowing each direction to peak separately.
 */
@Service
public class ArtifactTransferAdmissionService {

    private final Semaphore global;
    private final KeyedSemaphoreAdmission clients;

    public ArtifactTransferAdmissionService(IntegrationClientProperties properties) {
        this.global = new Semaphore(
                Math.max(1, properties.getMaxConcurrentArtifactTransfers()), true);
        this.clients = new KeyedSemaphoreAdmission(
                properties.getMaxConcurrentArtifactTransfersPerClient());
    }

    public Permit tryAcquire(String clientIdentity) {
        boolean globalAcquired = global.tryAcquire();
        KeyedSemaphoreAdmission.Permit client = globalAcquired
                ? clients.tryAcquire(clientIdentity)
                : null;
        if (client == null) {
            if (globalAcquired) {
                global.release();
            }
            return null;
        }
        return new Permit(global, client);
    }

    public static final class Permit implements AutoCloseable {
        private final Semaphore global;
        private final KeyedSemaphoreAdmission.Permit client;
        private boolean closed;

        private Permit(Semaphore global, KeyedSemaphoreAdmission.Permit client) {
            this.global = global;
            this.client = client;
        }

        @Override
        public synchronized void close() {
            if (!closed) {
                closed = true;
                client.close();
                global.release();
            }
        }
    }
}
