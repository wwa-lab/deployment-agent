package com.wwa.agenthub.platform.domain.integration.auth;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Keeps the presented bearer secret only for the current request thread so
 * client-authored persisted fields can reject exact credential disclosure.
 */
@Component
public class PresentedCredentialLeakGuard {

    private final ThreadLocal<String> presentedCredential = new ThreadLocal<>();

    public Scope bind(String credential) {
        String previous = presentedCredential.get();
        presentedCredential.set(credential);
        return new Scope(previous);
    }

    public boolean contains(String value) {
        String credential = presentedCredential.get();
        return credential != null
                && !credential.isEmpty()
                && value != null
                && value.contains(credential);
    }

    public boolean contains(byte[] value) {
        String credential = presentedCredential.get();
        if (credential == null || credential.isEmpty() || value == null) {
            return false;
        }
        byte[] needle = credential.getBytes(StandardCharsets.UTF_8);
        if (needle.length > value.length) {
            return false;
        }
        for (int offset = 0; offset <= value.length - needle.length; offset++) {
            int index = 0;
            while (index < needle.length && value[offset + index] == needle[index]) {
                index++;
            }
            if (index == needle.length) {
                return true;
            }
        }
        return false;
    }

    public final class Scope implements AutoCloseable {
        private final String previous;
        private boolean closed;

        private Scope(String previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (previous == null) {
                presentedCredential.remove();
            } else {
                presentedCredential.set(previous);
            }
        }

        @Override
        public String toString() {
            return "PresentedCredentialScope[redacted]";
        }
    }
}
