package com.wwa.agenthub.platform.domain.integration.auth;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/** Bounded in-process token buckets for the Atlas Integration HTTP surface. */
@Component
public class IntegrationRequestRateLimiter {

    private static final int MAX_BUCKETS = 10_000;
    private static final String OVERFLOW_BUCKET = "__overflow__";
    private static final long IDLE_NANOS = Duration.ofHours(1).toNanos();

    private final IntegrationClientProperties properties;
    private final LongSupplier nanoTime;
    private final BucketStore requestBuckets = new BucketStore();
    private final BucketStore authenticationAttemptBuckets = new BucketStore();

    @Autowired
    public IntegrationRequestRateLimiter(IntegrationClientProperties properties) {
        this(properties, System::nanoTime);
    }

    IntegrationRequestRateLimiter(
            IntegrationClientProperties properties,
            LongSupplier nanoTime
    ) {
        this.properties = properties;
        this.nanoTime = nanoTime;
    }

    public boolean tryAcquire(String identity) {
        return tryAcquire(requestBuckets, identity, capacity(), refillPerSecond());
    }

    /** Applies a budget to failed authentication responses by trusted remote address. */
    public boolean tryAcquireAuthenticationAttempt(String remoteAddress) {
        return tryAcquire(
                authenticationAttemptBuckets,
                remoteAddress,
                authenticationAttemptCapacity(),
                authenticationAttemptRefillPerSecond());
    }

    private boolean tryAcquire(
            BucketStore store,
            String identity,
            int capacity,
            double refillPerSecond
    ) {
        long now = nanoTime.getAsLong();
        evictIdleBuckets(store, now);
        String key = normalizedKey(store, identity);
        Bucket bucket = store.buckets.computeIfAbsent(key, ignored -> new Bucket(capacity, now));
        synchronized (bucket) {
            refill(bucket, now, capacity, refillPerSecond);
            if (bucket.tokens < 1d) {
                return false;
            }
            bucket.tokens -= 1d;
            return true;
        }
    }

    private static void refill(Bucket bucket, long now, int capacity, double refillPerSecond) {
        double elapsedSeconds = Math.max(0, now - bucket.lastRefillNanos) / 1_000_000_000d;
        bucket.tokens = Math.min(capacity, bucket.tokens + elapsedSeconds * refillPerSecond);
        bucket.lastRefillNanos = now;
        bucket.lastSeenNanos = now;
    }

    private static String normalizedKey(BucketStore store, String identity) {
        String candidate = identity == null || identity.isBlank() ? OVERFLOW_BUCKET : identity;
        if (store.buckets.containsKey(candidate) || store.buckets.size() < MAX_BUCKETS) {
            return candidate;
        }
        return OVERFLOW_BUCKET;
    }

    private static void evictIdleBuckets(BucketStore store, long now) {
        if ((store.acquisitions.incrementAndGet() & 1023L) != 0) {
            return;
        }
        store.buckets.entrySet().removeIf(entry -> !OVERFLOW_BUCKET.equals(entry.getKey())
                && now - entry.getValue().lastSeenNanos > IDLE_NANOS);
    }

    private int capacity() {
        return Math.max(1, properties.getRateLimitCapacity());
    }

    private double refillPerSecond() {
        return Math.max(0.01d, properties.getRateLimitRefillPerSecond());
    }

    private int authenticationAttemptCapacity() {
        return Math.max(1, properties.getAuthenticationAttemptRateLimitCapacity());
    }

    private double authenticationAttemptRefillPerSecond() {
        return Math.max(0.01d, properties.getAuthenticationAttemptRateLimitRefillPerSecond());
    }

    private static final class BucketStore {
        private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
        private final AtomicLong acquisitions = new AtomicLong();
    }

    private static final class Bucket {
        private double tokens;
        private long lastRefillNanos;
        private volatile long lastSeenNanos;

        private Bucket(double tokens, long now) {
            this.tokens = tokens;
            this.lastRefillNanos = now;
            this.lastSeenNanos = now;
        }
    }
}
