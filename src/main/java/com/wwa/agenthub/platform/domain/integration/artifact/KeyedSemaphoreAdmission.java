package com.wwa.agenthub.platform.domain.integration.artifact;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;

/** Exact keyed admission without hash-stripe collisions or an idle-key leak. */
final class KeyedSemaphoreAdmission {

    private final int permitsPerKey;
    private final ConcurrentMap<String, Entry> entries = new ConcurrentHashMap<>();

    KeyedSemaphoreAdmission(int permitsPerKey) {
        this.permitsPerKey = Math.max(1, permitsPerKey);
    }

    Permit tryAcquire(String key) {
        String stableKey = key == null || key.isBlank() ? "unknown" : key;
        Entry entry = entries.compute(stableKey, (ignored, current) -> {
            Entry selected = current == null ? new Entry(permitsPerKey) : current;
            selected.references += 1;
            return selected;
        });
        if (!entry.semaphore.tryAcquire()) {
            releaseReference(stableKey, entry);
            return null;
        }
        return new Permit(this, stableKey, entry);
    }

    private void releaseReference(String key, Entry expected) {
        entries.computeIfPresent(key, (ignored, current) -> {
            if (current != expected) {
                return current;
            }
            current.references -= 1;
            return current.references == 0 ? null : current;
        });
    }

    private static final class Entry {
        private final Semaphore semaphore;
        private int references;

        private Entry(int permits) {
            this.semaphore = new Semaphore(permits, true);
        }
    }

    static final class Permit implements AutoCloseable {
        private final KeyedSemaphoreAdmission owner;
        private final String key;
        private final Entry entry;
        private boolean closed;

        private Permit(KeyedSemaphoreAdmission owner, String key, Entry entry) {
            this.owner = owner;
            this.key = key;
            this.entry = entry;
        }

        @Override
        public synchronized void close() {
            if (!closed) {
                closed = true;
                entry.semaphore.release();
                owner.releaseReference(key, entry);
            }
        }
    }
}
