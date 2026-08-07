package com.wwa.agenthub.platform.domain.integration.auth;

import com.wwa.agenthub.contracts.enums.IntegrationClientType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.integration")
@Getter
@Setter
public class IntegrationClientProperties {

    private long maxArtifactBytes = 50L * 1024 * 1024;
    private long maxTextArtifactBytes = 5L * 1024 * 1024;
    private long maxJsonArtifactBytes = 5L * 1024 * 1024;
    private int maxJsonArtifactTokens = 100_000;
    private int maxJsonArtifactNestingDepth = 64;
    private int maxArtifactsPerExecution = 100;
    private long maxArtifactBytesPerExecution = 500L * 1024 * 1024;
    private int maxConcurrentArtifactTransfers = 2;
    private int maxConcurrentArtifactTransfersPerClient = 1;
    private int maxApprovedInputsPerTask = 100;
    private int maxArtifactsPerTask = 500;
    private long maxArtifactBytesPerTask = 1024L * 1024 * 1024;
    private int maxExecutionsPerTask = 10;
    private int maxProgressEventsPerExecution = 1000;
    private int rateLimitCapacity = 120;
    private double rateLimitRefillPerSecond = 2.0;
    private int authenticationAttemptRateLimitCapacity = 30;
    private double authenticationAttemptRateLimitRefillPerSecond = 0.5;
    private Duration idempotencyInProgressTimeout = Duration.ofMinutes(30);
    private Duration artifactContentRetention = Duration.ofDays(30);
    private int artifactRetentionCleanupBatchSize = 200;
    private int artifactRetentionCleanupMaxBatchesPerRun = 20;
    private boolean requireExternalArtifactScanner = true;
    private List<Client> clients = new ArrayList<>();

    @Getter
    @Setter
    public static class Client {
        private String tokenSha256;
        private String applicationId;
        private IntegrationClientType clientType;
        private String clientVersion;
        private String userId;
        private String displayName;
        private List<String> roles = new ArrayList<>();
        private List<String> permissions = new ArrayList<>();
        private List<String> scopes = new ArrayList<>();
        private List<String> allowedAgents = new ArrayList<>();
        private Instant expiresAt;
        private boolean enabled = true;
    }
}
