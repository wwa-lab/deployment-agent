package com.wwa.agenthub.platform.domain.integration.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wwa.agenthub.platform.domain.integration.IntegrationApiException;
import com.wwa.agenthub.platform.domain.integration.auth.IntegrationActor;
import com.wwa.agenthub.platform.domain.integration.auth.IntegrationClientProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class IdempotentCommandService {

    private static final HexFormat HEX = HexFormat.of();

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;
    private final IntegrationClientProperties properties;

    public <T> Result<T> execute(
            IntegrationActor actor,
            String method,
            String canonicalPath,
            String idempotencyKey,
            Object fingerprintSource,
            int responseStatus,
            Class<T> responseType,
            Supplier<T> command
    ) {
        return execute(
                actor,
                method,
                canonicalPath,
                idempotencyKey,
                fingerprintSource,
                responseStatus,
                responseType,
                () -> { },
                command);
    }

    public <T> Result<T> execute(
            IntegrationActor actor,
            String method,
            String canonicalPath,
            String idempotencyKey,
            Object fingerprintSource,
            int responseStatus,
            Class<T> responseType,
            Runnable replayGuard,
            Supplier<T> command
    ) {
        validateKey(idempotencyKey);
        String keyHash = digest(idempotencyKey);
        String fingerprint = fingerprint(fingerprintSource);

        Result<T> existing = readExisting(
                actor, method, canonicalPath, keyHash, fingerprint, responseType, replayGuard);
        if (existing != null) {
            return existing;
        }

        String reservationId;
        try {
            reservationId = reserve(
                    actor,
                    method,
                    canonicalPath,
                    keyHash,
                    fingerprint);
        } catch (ReservationExistsException | DataIntegrityViolationException collision) {
            Result<T> replay = readExisting(
                    actor, method, canonicalPath, keyHash, fingerprint, responseType, replayGuard);
            if (replay != null) {
                return replay;
            }
            throw collision;
        }

        try {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            return transaction.execute(status -> executeInTransaction(
                    reservationId,
                    fingerprint,
                    responseStatus,
                    command));
        } catch (RuntimeException exception) {
            releaseReservation(reservationId, fingerprint);
            throw exception;
        }
    }

    private String reserve(
            IntegrationActor actor,
            String method,
            String canonicalPath,
            String keyHash,
            String fingerprint
    ) {
        TransactionTemplate reservation = new TransactionTemplate(transactionManager);
        reservation.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return reservation.execute(status -> {
            IdempotencyRecord existing = repository.findCommandForUpdate(
                    actor.principalId(),
                    method.toUpperCase(),
                    canonicalPath,
                    keyHash).orElse(null);
            if (existing != null) {
                assertOwningClient(existing, actor);
                if (!existing.getRequestFingerprint().equals(fingerprint)) {
                    throw reused();
                }
                if (!isExpired(existing)) {
                    throw new ReservationExistsException();
                }
                repository.delete(existing);
                repository.flush();
            }
            IdempotencyRecord record = new IdempotencyRecord();
            record.setPrincipalId(actor.principalId());
            record.setClientApplicationId(actor.clientApplicationId());
            record.setHttpMethod(method.toUpperCase());
            record.setCanonicalPath(canonicalPath);
            record.setIdempotencyKeyHash(keyHash);
            record.setRequestFingerprint(fingerprint);
            record.setState(IdempotencyRecord.State.IN_PROGRESS);
            record.setExpiresAt(clock.instant().plus(properties.getIdempotencyInProgressTimeout()));
            repository.saveAndFlush(record);
            return record.getId();
        });
    }

    private <T> Result<T> executeInTransaction(
            String reservationId,
            String fingerprint,
            int responseStatus,
            Supplier<T> command
    ) {
        IdempotencyRecord record = repository.findById(reservationId)
                .filter(candidate -> candidate.getState() == IdempotencyRecord.State.IN_PROGRESS
                        && candidate.getRequestFingerprint().equals(fingerprint))
                .orElseThrow(() -> IntegrationApiException.conflict(
                        "IDEMPOTENCY_REQUEST_IN_PROGRESS",
                        "The idempotency reservation is no longer available.",
                        true));
        T body = command.get();
        record.setResponseStatus(responseStatus);
        record.setResponseBody(write(body));
        record.setState(IdempotencyRecord.State.COMPLETED);
        record.setCompletedAt(clock.instant());
        // The contract requires create/terminal commands for at least the
        // Execution lifetime and Artifact commands for at least the Artifact
        // metadata lifetime. Those resources are retained in this slice, so
        // completed replay records are deliberately non-expiring.
        record.setExpiresAt(null);
        repository.save(record);
        return new Result<>(body, responseStatus, false);
    }

    private void releaseReservation(String reservationId, String fingerprint) {
        TransactionTemplate cleanup = new TransactionTemplate(transactionManager);
        cleanup.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        cleanup.executeWithoutResult(status -> repository.findById(reservationId)
                .filter(record -> record.getState() == IdempotencyRecord.State.IN_PROGRESS)
                .filter(record -> record.getRequestFingerprint().equals(fingerprint))
                .ifPresent(repository::delete));
    }

    private <T> Result<T> readExisting(
            IntegrationActor actor,
            String method,
            String path,
            String keyHash,
            String fingerprint,
            Class<T> responseType,
            Runnable replayGuard
    ) {
        TransactionTemplate lookup = new TransactionTemplate(transactionManager);
        return lookup.execute(status -> readExistingInTransaction(
                actor, method, path, keyHash, fingerprint, responseType, replayGuard));
    }

    private <T> Result<T> readExistingInTransaction(
            IntegrationActor actor,
            String method,
            String path,
            String keyHash,
            String fingerprint,
            Class<T> responseType,
            Runnable replayGuard
    ) {
        IdempotencyRecord record = repository
                .findByPrincipalIdAndHttpMethodAndCanonicalPathAndIdempotencyKeyHash(
                        actor.principalId(),
                        method.toUpperCase(),
                        path,
                        keyHash)
                .orElse(null);
        if (record == null) {
            return null;
        }
        assertOwningClient(record, actor);
        if (!record.getRequestFingerprint().equals(fingerprint)) {
            throw reused();
        }
        if (isExpired(record)) {
            return null;
        }
        if (record.getState() != IdempotencyRecord.State.COMPLETED) {
            throw IntegrationApiException.conflict(
                    "IDEMPOTENCY_REQUEST_IN_PROGRESS",
                    "An identical request is still in progress.",
                    true);
        }
        // Reauthorization and attempt fencing are deliberately evaluated while
        // this replay lookup transaction is still open. Execution guards take
        // the same Task row lock as start/rerun, making replay linearizable
        // with creation of a newer attempt.
        replayGuard.run();
        return new Result<>(read(record.getResponseBody(), responseType), record.getResponseStatus(), true);
    }

    private String fingerprint(Object value) {
        JsonNode canonical = canonicalize(objectMapper.valueToTree(value));
        return HEX.formatHex(sha256(canonical.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private static String digest(String value) {
        return HEX.formatHex(sha256(value.getBytes(StandardCharsets.UTF_8)));
    }

    private JsonNode canonicalize(JsonNode node) {
        if (node == null || node.isNull() || node.isValueNode()) {
            return node;
        }
        if (node.isArray()) {
            ArrayNode array = objectMapper.createArrayNode();
            node.forEach(value -> array.add(canonicalize(value)));
            return array;
        }
        ObjectNode object = objectMapper.createObjectNode();
        Map<String, JsonNode> sorted = new TreeMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        fields.forEachRemaining(entry -> sorted.put(entry.getKey(), entry.getValue()));
        sorted.forEach((key, value) -> object.set(key, canonicalize(value)));
        return object;
    }

    private String write(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to store idempotent response", exception);
        }
    }

    private <T> T read(String body, Class<T> responseType) {
        try {
            return objectMapper.readValue(body, responseType);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to replay idempotent response", exception);
        }
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void validateKey(String key) {
        if (key == null || key.length() < 16 || key.length() > 128
                || key.chars().anyMatch(character -> character < 33 || character > 126)) {
            throw IntegrationApiException.badRequest(
                    "INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key must be 16-128 printable ASCII characters without whitespace.");
        }
    }

    private static IntegrationApiException reused() {
        return IntegrationApiException.conflict(
                "IDEMPOTENCY_KEY_REUSED",
                "The idempotency key was already used with a different request.",
                false);
    }

    private boolean isExpired(IdempotencyRecord record) {
        return record.getExpiresAt() != null && !record.getExpiresAt().isAfter(clock.instant());
    }

    private static void assertOwningClient(IdempotencyRecord record, IntegrationActor actor) {
        if (!record.getClientApplicationId().equals(actor.clientApplicationId())) {
            throw IntegrationApiException.forbidden(
                    "The idempotent response belongs to another Integration client.");
        }
    }

    private static final class ReservationExistsException extends RuntimeException {
    }

    public record Result<T>(T body, int responseStatus, boolean replayed) {
    }
}
