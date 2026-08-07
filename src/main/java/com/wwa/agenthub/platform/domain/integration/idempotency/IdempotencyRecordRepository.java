package com.wwa.agenthub.platform.domain.integration.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.time.Instant;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {

    Optional<IdempotencyRecord> findByPrincipalIdAndHttpMethodAndCanonicalPathAndIdempotencyKeyHash(
            String principalId,
            String httpMethod,
            String canonicalPath,
            String idempotencyKeyHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT record FROM IdempotencyRecord record
        WHERE record.principalId = :principalId
          AND record.httpMethod = :httpMethod
          AND record.canonicalPath = :canonicalPath
          AND record.idempotencyKeyHash = :idempotencyKeyHash
        """)
    Optional<IdempotencyRecord> findCommandForUpdate(
            @Param("principalId") String principalId,
            @Param("httpMethod") String httpMethod,
            @Param("canonicalPath") String canonicalPath,
            @Param("idempotencyKeyHash") String idempotencyKeyHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM IdempotencyRecord record WHERE record.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);
}
