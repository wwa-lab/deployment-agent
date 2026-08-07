package com.wwa.agenthub.platform.domain.integration.artifact;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import jakarta.persistence.LockModeType;

public interface IntegrationArtifactRepository extends JpaRepository<IntegrationArtifact, String> {

    @Query("""
        SELECT new com.wwa.agenthub.platform.domain.integration.artifact.IntegrationArtifactMetadata(
            a.id, a.task.id, a.execution.id, a.role, a.kind, a.name, a.mediaType,
            a.sizeBytes, a.sha256, a.sourcePath, a.storageMode, refArtifact.id, a.createdAt)
        FROM IntegrationArtifact a
        LEFT JOIN a.referenceArtifact refArtifact
        WHERE a.execution.id = :executionId
        ORDER BY a.createdAt ASC, a.id ASC
        """)
    List<IntegrationArtifactMetadata> findMetadataByExecutionId(
            @Param("executionId") String executionId);

    @Query("""
        SELECT new com.wwa.agenthub.platform.domain.integration.artifact.IntegrationArtifactMetadata(
            a.id, a.task.id, a.execution.id, a.role, a.kind, a.name, a.mediaType,
            a.sizeBytes, a.sha256, a.sourcePath, a.storageMode, refArtifact.id, a.createdAt)
        FROM TaskInputArtifactApproval approval
        JOIN approval.artifact a
        LEFT JOIN a.referenceArtifact refArtifact
        WHERE approval.task.id = :taskId
        ORDER BY approval.approvedAt ASC, approval.id ASC
        """)
    List<IntegrationArtifactMetadata> findApprovedMetadataByTaskId(@Param("taskId") String taskId);

    long countByExecutionId(String executionId);

    long countByTaskId(String taskId);

    @Query("SELECT COALESCE(SUM(a.sizeBytes), 0) FROM IntegrationArtifact a "
            + "WHERE a.execution.id = :executionId")
    long sumSizeBytesByExecutionId(@Param("executionId") String executionId);

    @Query("SELECT COALESCE(SUM(a.sizeBytes), 0) FROM IntegrationArtifact a "
            + "WHERE a.task.id = :taskId")
    long sumSizeBytesByTaskId(@Param("taskId") String taskId);

    boolean existsByIdAndExecutionId(String artifactId, String executionId);

    @EntityGraph(attributePaths = {"task", "task.request", "task.request.releaseFlow", "execution"})
    @Query("SELECT a FROM IntegrationArtifact a WHERE a.id = :artifactId")
    Optional<IntegrationArtifact> findWithProvenance(@Param("artifactId") String artifactId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "task", "task.request", "task.request.releaseFlow", "execution", "referenceArtifact"
    })
    @Query("SELECT a FROM IntegrationArtifact a WHERE a.id = :artifactId")
    Optional<IntegrationArtifact> findWithProvenanceForUpdate(@Param("artifactId") String artifactId);

    @Query("SELECT a.id FROM IntegrationArtifact a WHERE a.execution.id = :executionId")
    List<String> findArtifactIdsForSubmission(@Param("executionId") String executionId);

    @Query("""
        SELECT source.id
        FROM IntegrationArtifact artifact
        JOIN artifact.referenceArtifact source
        WHERE artifact.execution.id = :executionId
        """)
    List<String> findReferencedArtifactIdsForSubmission(
            @Param("executionId") String executionId);

    /** Row-only lock used after all local/reference IDs have been globally sorted. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM IntegrationArtifact a WHERE a.id = :artifactId")
    Optional<IntegrationArtifact> findByIdForSubmissionUpdate(@Param("artifactId") String artifactId);

    @Query("""
        SELECT artifact.id
        FROM IntegrationArtifact artifact
        WHERE artifact.storageMode = com.wwa.agenthub.contracts.enums.ArtifactStorageMode.UPLOAD
          AND artifact.content IS NOT NULL
          AND artifact.legalHold = false
          AND artifact.contentExpiresAt <= :now
        ORDER BY artifact.id
        """)
    List<String> findExpiredContentIds(@Param("now") Instant now, Pageable pageable);
}
