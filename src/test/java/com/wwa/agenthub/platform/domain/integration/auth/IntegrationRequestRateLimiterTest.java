package com.wwa.agenthub.platform.domain.integration.auth;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class IntegrationRequestRateLimiterTest {

    @Test
    void rejectsAfterBurstAndRefillsByConfiguredRate() {
        IntegrationClientProperties properties = new IntegrationClientProperties();
        properties.setRateLimitCapacity(2);
        properties.setRateLimitRefillPerSecond(1);
        AtomicLong now = new AtomicLong();
        IntegrationRequestRateLimiter limiter = new IntegrationRequestRateLimiter(
                properties, now::get);

        assertThat(limiter.tryAcquire("copilot:alice")).isTrue();
        assertThat(limiter.tryAcquire("copilot:alice")).isTrue();
        assertThat(limiter.tryAcquire("copilot:alice")).isFalse();

        now.addAndGet(1_000_000_000L);
        assertThat(limiter.tryAcquire("copilot:alice")).isTrue();
        assertThat(limiter.tryAcquire("copilot:alice")).isFalse();
        assertThat(limiter.tryAcquire("kiro:alice")).isTrue();
    }

    @Test
    void authenticationAttemptBucketsAreRemoteAddressScoped() {
        IntegrationClientProperties properties = new IntegrationClientProperties();
        properties.setAuthenticationAttemptRateLimitCapacity(2);
        properties.setAuthenticationAttemptRateLimitRefillPerSecond(1);
        AtomicLong now = new AtomicLong();
        IntegrationRequestRateLimiter limiter = new IntegrationRequestRateLimiter(
                properties, now::get);

        assertThat(limiter.tryAcquireAuthenticationAttempt("198.51.100.10")).isTrue();
        assertThat(limiter.tryAcquireAuthenticationAttempt("198.51.100.10")).isTrue();
        assertThat(limiter.tryAcquireAuthenticationAttempt("198.51.100.10")).isFalse();
        assertThat(limiter.tryAcquireAuthenticationAttempt("198.51.100.11")).isTrue();
    }
}
