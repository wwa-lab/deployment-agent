package com.wwa.agenthub.platform.integration;

import com.wwa.agenthub.contracts.AccessScope;
import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.enums.IntegrationClientType;
import com.wwa.agenthub.platform.domain.integration.IntegrationApiException;
import com.wwa.agenthub.platform.domain.integration.auth.IntegrationActor;
import com.wwa.agenthub.platform.domain.integration.idempotency.IdempotencyRecord;
import com.wwa.agenthub.platform.domain.integration.idempotency.IdempotencyRecordRepository;
import com.wwa.agenthub.platform.domain.integration.idempotency.IdempotentCommandService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class IdempotentCommandServiceTest {

    @Autowired private IdempotentCommandService service;
    @Autowired private IdempotencyRecordRepository repository;

    @Test
    void storesOnlyKeyDigestAndRetainsCompletedReplayForResourceLifetime() {
        String key = "raw-key-must-not-persist-0001";
        IntegrationActor actor = actor("idem-hash-user", "client-a");

        var result = execute(actor, key, () -> new TestResponse("resource-1"));

        assertThat(result.replayed()).isFalse();
        IdempotencyRecord record = repository
                .findByPrincipalIdAndHttpMethodAndCanonicalPathAndIdempotencyKeyHash(
                        actor.principalId(), "POST", "/commands/hash", sha256(key))
                .orElseThrow();
        assertThat(record.getIdempotencyKeyHash()).isEqualTo(sha256(key));
        assertThat(record.getIdempotencyKeyHash()).doesNotContain(key);
        assertThat(record.getState()).isEqualTo(IdempotencyRecord.State.COMPLETED);
        assertThat(record.getExpiresAt()).isNull();
    }

    @Test
    void rejectsLiveReservationAndReclaimsExpiredReservation() {
        IntegrationActor actor = actor("idem-reservation-user", "client-a");
        String liveKey = "live-reservation-key-00001";
        saveReservation(actor, liveKey, Instant.now().plusSeconds(300));

        assertThatThrownBy(() -> execute(actor, liveKey, () -> new TestResponse("never")))
                .isInstanceOfSatisfying(IntegrationApiException.class, error ->
                        assertThat(error.getCode()).isEqualTo("IDEMPOTENCY_REQUEST_IN_PROGRESS"));

        String staleKey = "stale-reservation-key-001";
        saveReservation(actor, staleKey, Instant.now().minusSeconds(1));
        var reclaimed = execute(actor, staleKey, () -> new TestResponse("reclaimed"));

        assertThat(reclaimed.body().resourceId()).isEqualTo("reclaimed");
        assertThat(reclaimed.replayed()).isFalse();
    }

    @Test
    void namespaceIsPrincipalMethodPathAndKeyButReplayRemainsClientBound() {
        String key = "principal-namespace-key-001";
        IntegrationActor first = actor("same-principal-user", "client-a");
        IntegrationActor second = actor("same-principal-user", "client-b");
        execute(first, key, () -> new TestResponse("resource-1"));

        assertThatThrownBy(() -> execute(second, key, () -> new TestResponse("resource-1")))
                .isInstanceOfSatisfying(IntegrationApiException.class, error ->
                        assertThat(error.getCode()).isEqualTo("FORBIDDEN"));
    }

    @Test
    void completedReplayRunsTheCurrentAuthorizationFenceBeforeReturningStoredResponse() {
        String key = "replay-current-fence-key-01";
        IntegrationActor actor = actor("replay-fence-user", "client-a");
        execute(actor, key, () -> new TestResponse("attempt-1"));
        AtomicBoolean commandInvoked = new AtomicBoolean();

        assertThatThrownBy(() -> service.execute(
                actor,
                "POST",
                "/commands/hash",
                key,
                Map.of("request", "same"),
                201,
                TestResponse.class,
                () -> {
                    throw IntegrationApiException.conflict(
                            "STALE_EXECUTION", "A newer attempt exists.", false);
                },
                () -> {
                    commandInvoked.set(true);
                    return new TestResponse("must-not-run");
                }))
                .isInstanceOfSatisfying(IntegrationApiException.class, error ->
                        assertThat(error.getCode()).isEqualTo("STALE_EXECUTION"));
        assertThat(commandInvoked).isFalse();
    }

    private IdempotentCommandService.Result<TestResponse> execute(
            IntegrationActor actor,
            String key,
            java.util.function.Supplier<TestResponse> command
    ) {
        return service.execute(
                actor,
                "POST",
                "/commands/hash",
                key,
                Map.of("request", "same"),
                201,
                TestResponse.class,
                command);
    }

    private void saveReservation(IntegrationActor actor, String key, Instant expiresAt) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setPrincipalId(actor.principalId());
        record.setClientApplicationId(actor.clientApplicationId());
        record.setHttpMethod("POST");
        record.setCanonicalPath("/commands/hash");
        record.setIdempotencyKeyHash(sha256(key));
        record.setRequestFingerprint(sha256("{\"request\":\"same\"}"));
        record.setState(IdempotencyRecord.State.IN_PROGRESS);
        record.setExpiresAt(expiresAt);
        repository.saveAndFlush(record);
    }

    private static IntegrationActor actor(String userId, String clientId) {
        UserContext user = new UserContext(
                userId,
                "DEVELOPER",
                List.of("DEVELOPER"),
                Set.of(),
                userId,
                List.of(new AccessScope("*", "*")));
        return new IntegrationActor(
                user,
                clientId,
                IntegrationClientType.MANUAL,
                "1.0",
                Set.of("deployment-agent"),
                true);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record TestResponse(String resourceId) {
    }
}
