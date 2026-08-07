package com.wwa.agenthub.platform.domain.integration.artifact;

import com.wwa.agenthub.platform.domain.integration.auth.IntegrationClientProperties;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

import java.util.List;

/** Fails deployment startup when the production Artifact scan gate is not wired. */
@Component
public class ArtifactScannerReadiness implements SmartInitializingSingleton {

    private final List<ArtifactMalwareScanner> scanners;
    private final IntegrationClientProperties properties;

    public ArtifactScannerReadiness(
            List<ArtifactMalwareScanner> scanners,
            IntegrationClientProperties properties
    ) {
        this.scanners = List.copyOf(scanners);
        this.properties = properties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (properties.isRequireExternalArtifactScanner()
                && scanners.stream().noneMatch(ArtifactMalwareScanner::productionReady)) {
            throw new IllegalStateException(
                    "Atlas Artifact uploads require a production-ready malware/DLP scanner bean");
        }
    }
}
