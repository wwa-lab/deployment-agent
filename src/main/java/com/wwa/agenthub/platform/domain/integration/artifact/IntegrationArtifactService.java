package com.wwa.agenthub.platform.domain.integration.artifact;

import com.wwa.agenthub.contracts.dto.integration.ArtifactUploadMetadata;
import com.wwa.agenthub.contracts.dto.integration.ExternalArtifactRequest;
import com.wwa.agenthub.contracts.dto.integration.IntegrationArtifactDto;
import com.wwa.agenthub.contracts.enums.ArtifactRole;
import com.wwa.agenthub.contracts.enums.ArtifactStorageMode;
import com.wwa.agenthub.contracts.enums.AuditActionType;
import com.wwa.agenthub.contracts.enums.ExecutionEventType;
import com.wwa.agenthub.contracts.enums.PermissionKey;
import com.wwa.agenthub.contracts.enums.TaskStatus;
import com.wwa.agenthub.domain.audit.AuditLoggerService;
import com.wwa.agenthub.domain.releaseflow.Request;
import com.wwa.agenthub.domain.task.Task;
import com.wwa.agenthub.domain.task.TaskExecutionHistory;
import com.wwa.agenthub.domain.task.TaskExecutionHistoryRepository;
import com.wwa.agenthub.domain.task.TaskRepository;
import com.wwa.agenthub.platform.domain.integration.IntegrationApiException;
import com.wwa.agenthub.platform.domain.integration.IntegrationProjectionService;
import com.wwa.agenthub.platform.domain.integration.SensitiveTextRedactor;
import com.wwa.agenthub.platform.domain.integration.auth.IntegrationActor;
import com.wwa.agenthub.platform.domain.integration.auth.IntegrationAuthorizationService;
import com.wwa.agenthub.platform.domain.integration.auth.IntegrationClientProperties;
import com.wwa.agenthub.platform.domain.integration.event.ExecutionEventService;
import com.wwa.agenthub.platform.domain.integration.lifecycle.ExecutionLifecycleService;
import com.wwa.agenthub.platform.web.common.CorrelationIdFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class IntegrationArtifactService {

    private static final HexFormat HEX = HexFormat.of();
    private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg");
    private static final Set<String> TEXT_TYPES = Set.of("text/plain", "text/markdown");
    private static final Set<String> BLOCKED_MEDIA_TYPES = Set.of(
            "application/gzip",
            "application/java-archive",
            "application/vnd.rar",
            "application/x-7z-compressed",
            "application/x-bzip2",
            "application/x-rar-compressed",
            "application/x-tar",
            "application/x-zip-compressed",
            "application/zip",
            "image/svg+xml",
            "text/html",
            "text/javascript");

    private final IntegrationArtifactRepository artifactRepository;
    private final TaskInputArtifactApprovalRepository approvalRepository;
    private final TaskExecutionHistoryRepository executionRepository;
    private final TaskRepository taskRepository;
    private final ExecutionLifecycleService lifecycleService;
    private final IntegrationAuthorizationService authorizationService;
    private final IntegrationProjectionService projectionService;
    private final ExecutionEventService eventService;
    private final AuditLoggerService auditLogger;
    private final IntegrationClientProperties properties;
    private final ArtifactContentSecurityPolicy contentSecurityPolicy;
    private final ArtifactRetentionService retentionService;

    @Transactional
    public IntegrationArtifactDto upload(
            String executionId,
            ArtifactUploadMetadata metadata,
            byte[] content,
        IntegrationActor actor
    ) {
        ExecutionLifecycleService.ActiveExecution active = lifecycleService.lockActive(executionId, actor);
        byte[] safeContent = content == null ? new byte[0] : content;
        validateUpload(metadata, safeContent);
        assertArtifactQuota(active.task().getId(), executionId, safeContent.length);

        IntegrationArtifact artifact = new IntegrationArtifact();
        artifact.setTask(active.task());
        artifact.setExecution(active.execution());
        artifact.setRole(metadata.role());
        artifact.setKind(normalizeKind(metadata.kind()));
        artifact.setName(metadata.name().trim());
        artifact.setMediaType(normalizeMediaType(metadata.mediaType()));
        artifact.setSizeBytes(safeContent.length);
        artifact.setSha256(metadata.digest().value());
        artifact.setSourcePath(normalizeSourcePath(metadata.sourcePath()));
        artifact.setStorageMode(ArtifactStorageMode.UPLOAD);
        artifact.setContent(safeContent);
        artifact.setContentExpiresAt(retentionService.contentExpiry());
        artifact.setCreatedBy(actor.principalId());
        artifact.setClientApplicationId(actor.clientApplicationId());
        artifact.setCorrelationId(CorrelationIdFilter.current());
        artifact = artifactRepository.save(artifact);

        refreshArtifactCount(active.execution());
        appendEvent(active.execution(), artifact, actor);
        audit(active.execution(), artifact, actor);
        return projectionService.toArtifact(artifact);
    }

    @Transactional
    public IntegrationArtifactDto reference(
            String executionId,
            ExternalArtifactRequest request,
            IntegrationActor actor
    ) {
        ExecutionLifecycleService.ActiveExecution active = lifecycleService.lockActive(executionId, actor);
        ArtifactUploadMetadata metadata = request.metadata();
        validateReferenceMetadata(metadata);
        assertArtifactQuota(active.task().getId(), executionId, metadata.sizeBytes());
        IntegrationArtifact source = artifactRepository.findWithProvenanceForUpdate(request.referenceId())
                .orElseThrow(() -> IntegrationApiException.notFound("ARTIFACT_NOT_FOUND", "Artifact"));
        authorizationService.assertExecutionVisible(source.getExecution(), actor);
        if (source.getStorageMode() == ArtifactStorageMode.REFERENCE) {
            throw IntegrationApiException.badRequest(
                    "ARTIFACT_POLICY_VIOLATION",
                    "Reference chains are not supported.");
        }
        assertCompatibleProvenance(active.execution(), source.getExecution());
        assertReferenceMatches(metadata, source);
        assertContentAvailable(source);
        extendContentRetention(source);

        IntegrationArtifact artifact = new IntegrationArtifact();
        artifact.setTask(active.task());
        artifact.setExecution(active.execution());
        artifact.setRole(metadata.role());
        artifact.setKind(normalizeKind(metadata.kind()));
        artifact.setName(metadata.name().trim());
        artifact.setMediaType(normalizeMediaType(metadata.mediaType()));
        artifact.setSizeBytes(metadata.sizeBytes());
        artifact.setSha256(metadata.digest().value());
        artifact.setSourcePath(normalizeSourcePath(metadata.sourcePath()));
        artifact.setStorageMode(ArtifactStorageMode.REFERENCE);
        artifact.setReferenceArtifact(source);
        artifact.setCreatedBy(actor.principalId());
        artifact.setClientApplicationId(actor.clientApplicationId());
        artifact.setCorrelationId(CorrelationIdFilter.current());
        artifact = artifactRepository.save(artifact);

        refreshArtifactCount(active.execution());
        appendEvent(active.execution(), artifact, actor);
        audit(active.execution(), artifact, actor);
        return projectionService.toArtifact(artifact);
    }

    @Transactional(readOnly = true)
    public List<IntegrationArtifactDto> list(String executionId, IntegrationActor actor) {
        TaskExecutionHistory execution = executionRepository.findIntegrationExecutionById(executionId)
                .orElseThrow(() -> IntegrationApiException.notFound("EXECUTION_NOT_FOUND", "Execution"));
        authorizationService.assertExecutionVisible(execution, actor);
        return artifactRepository.findMetadataByExecutionId(executionId).stream()
                .map(artifact -> projectionService.toArtifact(artifact, execution.getTask()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ArtifactContent download(
            String executionId,
            String artifactId,
            IntegrationActor actor
    ) {
        IntegrationArtifact artifact = artifactRepository.findWithProvenance(artifactId)
                .orElseThrow(() -> IntegrationApiException.notFound("ARTIFACT_NOT_FOUND", "Artifact"));
        if (!executionId.equals(artifact.getExecution().getId())) {
            throw IntegrationApiException.notFound("ARTIFACT_NOT_FOUND", "Artifact");
        }
        authorizationService.assertExecutionVisible(artifact.getExecution(), actor);
        return contentOf(artifact);
    }

    @Transactional(readOnly = true)
    public List<IntegrationArtifactDto> listApprovedInputs(String taskId, IntegrationActor actor) {
        Task task = taskRepository.findIntegrationTaskById(taskId)
                .orElseThrow(() -> IntegrationApiException.notFound("TASK_NOT_FOUND", "Task"));
        authorizationService.assertTaskVisible(task, actor);
        return artifactRepository.findApprovedMetadataByTaskId(taskId).stream()
                .map(artifact -> projectionService.toApprovedInputArtifact(artifact, task))
                .toList();
    }

    @Transactional(readOnly = true)
    public ArtifactContent downloadApprovedInput(
            String taskId,
            String artifactId,
            IntegrationActor actor
    ) {
        Task task = taskRepository.findIntegrationTaskById(taskId)
                .orElseThrow(() -> IntegrationApiException.notFound("TASK_NOT_FOUND", "Task"));
        authorizationService.assertTaskVisible(task, actor);
        TaskInputArtifactApproval approval = approvalRepository
                .findByTaskIdAndArtifactId(taskId, artifactId)
                .orElseGet(() -> deniedOrHiddenInput(artifactId, actor));
        assertCompatibleProvenance(task, approval.getArtifact());
        authorizationService.assertExecutionScopeVisible(
                approval.getArtifact().getExecution(), actor);
        return contentOf(approval.getArtifact());
    }

    /** Platform-internal provisioning seam; no CLI endpoint can self-approve an input. */
    @Transactional
    public void approveInput(Task task, IntegrationArtifact artifact, String approverId) {
        if (task == null
                || artifact == null
                || approverId == null
                || approverId.isBlank()
                || !authorizationService.isIntegrationReady(task)
                || artifact.getStorageMode() == ArtifactStorageMode.REFERENCE) {
            throw policy("Only an immutable uploaded Atlas Artifact can be approved as input.");
        }
        artifact = artifactRepository.findWithProvenanceForUpdate(artifact.getId())
                .orElseThrow(() -> IntegrationApiException.notFound("ARTIFACT_NOT_FOUND", "Artifact"));
        assertContentAvailable(artifact);
        artifact.setLegalHold(true);
        assertCompatibleProvenance(task, artifact);
        if (approvalRepository.findByTaskIdAndArtifactId(task.getId(), artifact.getId()).isPresent()) {
            return;
        }
        if (approvalRepository.countByTaskId(task.getId()) >= properties.getMaxApprovedInputsPerTask()) {
            throw policy("The Task approved-input Artifact quota has been reached.");
        }
        TaskInputArtifactApproval approval = new TaskInputArtifactApproval();
        approval.setTask(task);
        approval.setArtifact(artifact);
        approval.setApprovedBy(approverId);
        approvalRepository.save(approval);
    }

    /** Control-plane command for approving immutable output as a downstream Task input. */
    @Transactional(readOnly = true)
    public void authorizeApproveInput(
            String taskId,
            String artifactId,
            IntegrationActor actor
    ) {
        Task task = taskRepository.findIntegrationTaskById(taskId)
                .orElseThrow(() -> IntegrationApiException.notFound("TASK_NOT_FOUND", "Task"));
        authorizationService.assertTaskVisible(task, actor);
        assertHumanInputApprover(actor);
        IntegrationArtifact artifact = artifactRepository.findWithProvenance(artifactId)
                .orElseThrow(() -> IntegrationApiException.notFound("ARTIFACT_NOT_FOUND", "Artifact"));
        authorizationService.assertExecutionVisible(artifact.getExecution(), actor);
        assertCompatibleProvenance(task, artifact);
    }

    /** Control-plane command for approving immutable output as a downstream Task input. */
    @Transactional
    public IntegrationArtifactDto approveInput(
            String taskId,
            String artifactId,
            IntegrationActor actor
    ) {
        Task task = taskRepository.findByIdForExecutionUpdate(taskId)
                .orElseThrow(() -> IntegrationApiException.notFound("TASK_NOT_FOUND", "Task"));
        authorizationService.assertTaskVisible(task, actor);
        assertHumanInputApprover(actor);
        if (task.getActiveExecutionId() != null
                || task.getTaskStatus() != TaskStatus.Ready_For_Execution) {
            throw IntegrationApiException.conflict(
                    "INPUT_APPROVAL_NOT_AVAILABLE",
                    "Inputs can be approved only before a Ready Task starts.",
                    false);
        }
        TaskInputArtifactApproval existing = approvalRepository
                .findByTaskIdAndArtifactId(taskId, artifactId)
                .orElse(null);
        if (existing != null) {
            return projectionService.toApprovedInputArtifact(existing.getArtifact(), task);
        }
        IntegrationArtifact artifact = artifactRepository.findWithProvenanceForUpdate(artifactId)
                .orElseThrow(() -> IntegrationApiException.notFound("ARTIFACT_NOT_FOUND", "Artifact"));
        authorizationService.assertExecutionVisible(artifact.getExecution(), actor);
        if (artifact.getExecution().getExecutionStatus()
                != com.wwa.agenthub.contracts.enums.ExecutionStatus.Completed
                || artifact.getTask().getTaskStatus() != TaskStatus.Approved) {
            throw policy(
                    "Only an Artifact from a completed, human-approved source Task can become input.");
        }
        approveInput(task, artifact, actor.principalId());

        Request request = task.getRequest();
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("artifactId", artifactId);
        context.put("application", request.getApplication());
        context.put("snowGroup", request.getSnowGroup());
        context.put("agent", request.getAgent());
        auditLogger.logAtomic(
                actor.user(),
                AuditActionType.integration_input_artifact_approve,
                request.getReleaseFlow().getId(),
                request.getId(),
                task.getId(),
                context);
        return projectionService.toApprovedInputArtifact(artifact, task);
    }

    private static void assertHumanInputApprover(IntegrationActor actor) {
        if (actor.bearerAuthenticated()) {
            throw IntegrationApiException.forbidden(
                    "A human Web session is required for approved-input provisioning.");
        }
        if (!actor.user().isGlobalDevOpsAdmin()
                && !actor.user().hasPermission(PermissionKey.PLATFORM_ACCESS_MANAGE.value())) {
            throw IntegrationApiException.forbidden(
                    "Approved-input provisioning requires platform access administration permission.");
        }
    }

    private void validateUpload(ArtifactUploadMetadata metadata, byte[] content) {
        validateMetadata(metadata);
        if (metadata.sizeBytes() != content.length) {
            throw policy("Declared and actual Artifact sizes must match.");
        }
        if (content.length > properties.getMaxArtifactBytes()) {
            throw new IntegrationApiException(
                    org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE,
                    "ARTIFACT_TOO_LARGE",
                    "The Artifact exceeds the configured size limit.",
                    false);
        }
        String actualDigest = HEX.formatHex(sha256(content));
        if (!MessageDigest.isEqual(
                actualDigest.getBytes(StandardCharsets.US_ASCII),
                metadata.digest().value().getBytes(StandardCharsets.US_ASCII))) {
            throw IntegrationApiException.unprocessable(
                    "ARTIFACT_DIGEST_MISMATCH",
                    "Artifact SHA-256 does not match.");
        }
        String mediaType = normalizeMediaType(metadata.mediaType());
        String sourcePath = normalizeSourcePath(metadata.sourcePath());
        validateContent(mediaType, content);
        contentSecurityPolicy.assertAllowed(metadata, mediaType, sourcePath, content);
    }

    private void validateMetadata(ArtifactUploadMetadata metadata) {
        if (metadata == null || metadata.digest() == null) {
            throw policy("Artifact metadata and digest are required.");
        }
        if (metadata.role() != ArtifactRole.OUTPUT && metadata.role() != ArtifactRole.EVIDENCE) {
            throw policy("Execution Artifact role must be OUTPUT or EVIDENCE.");
        }
        String normalizedKind = normalizeKind(metadata.kind());
        validateName(metadata.name());
        normalizeMediaType(metadata.mediaType());
        String normalizedSourcePath = normalizeSourcePath(metadata.sourcePath());
        assertNoSensitiveMetadata(normalizedKind, metadata.name().trim(), normalizedSourcePath);
        if (!"SHA-256".equals(metadata.digest().algorithm())
                || metadata.digest().value() == null
                || !metadata.digest().value().matches("[a-f0-9]{64}")) {
            throw policy("Artifact digest must be a lowercase SHA-256 value.");
        }
    }

    private void validateReferenceMetadata(ArtifactUploadMetadata metadata) {
        validateMetadata(metadata);
        if (metadata.sizeBytes() > properties.getMaxArtifactBytes()) {
            throw new IntegrationApiException(
                    org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE,
                    "ARTIFACT_TOO_LARGE",
                    "The Artifact exceeds the configured size limit.",
                    false);
        }
    }

    private void assertReferenceMatches(ArtifactUploadMetadata metadata, IntegrationArtifact source) {
        boolean matches = metadata.sizeBytes() == source.getSizeBytes()
                && metadata.role() == source.getRole()
                && metadata.digest().value().equals(source.getSha256())
                && normalizeMediaType(metadata.mediaType()).equals(source.getMediaType())
                && normalizeKind(metadata.kind()).equals(source.getKind())
                && metadata.name().trim().equals(source.getName())
                && Objects.equals(
                        normalizeSourcePath(metadata.sourcePath()), source.getSourcePath());
        if (!matches) {
            throw IntegrationApiException.unprocessable(
                    "ARTIFACT_POLICY_VIOLATION",
                    "Artifact metadata does not match the immutable Atlas reference.");
        }
    }

    private void validateContent(String mediaType, byte[] content) {
        if (BLOCKED_MEDIA_TYPES.contains(mediaType)
                || looksLikeArchive(content)
                || looksLikeExecutable(content)) {
            throw unsupportedMedia(
                    "Repository archives, executables, and active content are not accepted as Artifacts.");
        }
        if (TEXT_TYPES.contains(mediaType)) {
            requireUtf8(content);
            return;
        }
        if ("application/json".equals(mediaType)) {
            requireUtf8(content);
            return;
        }
        if ("application/pdf".equals(mediaType)) {
            if (!startsWith(content, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
                throw policy("PDF Artifact signature is invalid.");
            }
            return;
        }
        if (IMAGE_TYPES.contains(mediaType)) {
            boolean valid = "image/png".equals(mediaType)
                    ? startsWith(content, new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a})
                    : startsWith(content, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
            if (!valid) {
                throw policy("Image Artifact signature is invalid.");
            }
            return;
        }
        throw unsupportedMedia(
                "Artifact media type is not supported; unclassified binary content requires external scanning.");
    }

    private static void validateName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty()
                || normalized.length() > 255
                || normalized.contains("/")
                || normalized.contains("\\")
                || ".".equals(normalized)
                || "..".equals(normalized)
                || normalized.chars().anyMatch(character -> character < 32 || character == 127)) {
            throw policy("Artifact name must be a safe basename.");
        }
    }

    private static String normalizeSourcePath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String normalized = path.trim().replace('\\', '/');
        if (normalized.length() > 1024
                || normalized.startsWith("/")
                || normalized.matches("^[A-Za-z]:.*")
                || List.of(normalized.split("/")).contains("..")
                || normalized.chars().anyMatch(character -> character < 32 || character == 127)) {
            throw policy("Artifact sourcePath must be a safe relative label.");
        }
        return normalized;
    }

    private static void requireUtf8(byte[] content) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content));
        } catch (CharacterCodingException exception) {
            throw policy("Text Artifact content must be valid UTF-8.");
        }
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (content[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private static boolean looksLikeArchive(byte[] content) {
        return startsWith(content, new byte[]{0x50, 0x4b, 0x03, 0x04})
                || startsWith(content, new byte[]{0x50, 0x4b, 0x05, 0x06})
                || startsWith(content, new byte[]{0x50, 0x4b, 0x07, 0x08})
                || startsWith(content, new byte[]{0x1f, (byte) 0x8b})
                || startsWith(content, new byte[]{0x37, 0x7a, (byte) 0xbc, (byte) 0xaf, 0x27, 0x1c})
                || startsWith(content, new byte[]{0x52, 0x61, 0x72, 0x21})
                || matchesAt(content, 257, "ustar".getBytes(StandardCharsets.US_ASCII));
    }

    private static boolean looksLikeExecutable(byte[] content) {
        return startsWith(content, new byte[]{0x7f, 0x45, 0x4c, 0x46})
                || startsWith(content, new byte[]{0x4d, 0x5a})
                || startsWith(content, new byte[]{(byte) 0xca, (byte) 0xfe, (byte) 0xba, (byte) 0xbe})
                || startsWith(content, new byte[]{0x00, 0x61, 0x73, 0x6d})
                || startsWith(content, new byte[]{0x23, 0x21})
                || startsWith(content, new byte[]{(byte) 0xfe, (byte) 0xed, (byte) 0xfa, (byte) 0xce})
                || startsWith(content, new byte[]{(byte) 0xfe, (byte) 0xed, (byte) 0xfa, (byte) 0xcf})
                || startsWith(content, new byte[]{(byte) 0xce, (byte) 0xfa, (byte) 0xed, (byte) 0xfe})
                || startsWith(content, new byte[]{(byte) 0xcf, (byte) 0xfa, (byte) 0xed, (byte) 0xfe});
    }

    private static boolean matchesAt(byte[] content, int offset, byte[] value) {
        if (offset < 0 || content.length < offset + value.length) {
            return false;
        }
        for (int index = 0; index < value.length; index++) {
            if (content[offset + index] != value[index]) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeKind(String kind) {
        String normalized = kind == null ? "" : kind.trim();
        if (normalized.isEmpty()
                || normalized.length() > 128
                || normalized.chars().anyMatch(character -> character < 32 || character == 127)) {
            throw policy("Artifact kind must be a safe non-empty value.");
        }
        return normalized;
    }

    private static String normalizeMediaType(String mediaType) {
        if (mediaType == null || mediaType.isBlank()) {
            throw policy("Artifact media type is required.");
        }
        return mediaType.split(";", 2)[0].trim().toLowerCase();
    }

    private static void assertNoSensitiveMetadata(String... values) {
        for (String value : values) {
            if (value != null && !SensitiveTextRedactor.redact(value).equals(value)) {
                throw policy("Artifact metadata cannot contain credentials or secret material.");
            }
        }
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void refreshArtifactCount(TaskExecutionHistory execution) {
        execution.setArtifactCount(Math.toIntExact(artifactRepository.countByExecutionId(execution.getId())));
    }

    private void assertArtifactQuota(String taskId, String executionId, long incomingBytes) {
        long currentCount = artifactRepository.countByExecutionId(executionId);
        long currentBytes = artifactRepository.sumSizeBytesByExecutionId(executionId);
        boolean countExceeded = currentCount >= properties.getMaxArtifactsPerExecution();
        boolean bytesExceeded = incomingBytes > properties.getMaxArtifactBytesPerExecution()
                || currentBytes > properties.getMaxArtifactBytesPerExecution() - incomingBytes;
        if (countExceeded || bytesExceeded) {
            throw new IntegrationApiException(
                    org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE,
                    "ARTIFACT_EXECUTION_QUOTA_EXCEEDED",
                    "The Execution Artifact quota has been reached.",
                    false);
        }
        long taskCount = artifactRepository.countByTaskId(taskId);
        long taskBytes = artifactRepository.sumSizeBytesByTaskId(taskId);
        boolean taskCountExceeded = taskCount >= properties.getMaxArtifactsPerTask();
        boolean taskBytesExceeded = incomingBytes > properties.getMaxArtifactBytesPerTask()
                || taskBytes > properties.getMaxArtifactBytesPerTask() - incomingBytes;
        if (taskCountExceeded || taskBytesExceeded) {
            throw new IntegrationApiException(
                    org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE,
                    "ARTIFACT_TASK_QUOTA_EXCEEDED",
                    "The Task cumulative Artifact quota has been reached.",
                    false);
        }
    }

    private static void assertCompatibleProvenance(
            TaskExecutionHistory target,
            TaskExecutionHistory source
    ) {
        if (!Objects.equals(target.getConfigApplication(), source.getConfigApplication())
                || !Objects.equals(target.getConfigSnowGroup(), source.getConfigSnowGroup())
                || !Objects.equals(target.getConfigAgent(), source.getConfigAgent())
                || !Objects.equals(target.getProjectId(), source.getProjectId())) {
            throw policy("Artifact references cannot cross application, team, Agent, or project boundaries.");
        }
    }

    private static void assertCompatibleProvenance(Task target, IntegrationArtifact artifact) {
        TaskExecutionHistory source = artifact.getExecution();
        Request request = target.getRequest();
        if (source == null
                || !source.isIntegrationManaged()
                || !Objects.equals(request.getApplication(), source.getConfigApplication())
                || !Objects.equals(request.getSnowGroup(), source.getConfigSnowGroup())
                || !Objects.equals(request.getAgent(), source.getConfigAgent())
                || !Objects.equals(
                        request.getReleaseFlow().getProjectId(), source.getProjectId())) {
            throw policy("Approved input Artifacts must share application, team, Agent, and project provenance.");
        }
    }

    private void appendEvent(
            TaskExecutionHistory execution,
            IntegrationArtifact artifact,
            IntegrationActor actor
    ) {
        eventService.append(
                execution,
                ExecutionEventType.ARTIFACT_REGISTERED,
                null,
                null,
                "Artifact registered",
                null,
                Map.of(
                        "artifactId", artifact.getId(),
                        "role", artifact.getRole().name(),
                        "kind", artifact.getKind(),
                        "sizeBytes", artifact.getSizeBytes()),
                actor,
                CorrelationIdFilter.current());
    }

    private void audit(
            TaskExecutionHistory execution,
            IntegrationArtifact artifact,
            IntegrationActor actor
    ) {
        Request request = execution.getTask().getRequest();
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("executionId", execution.getId());
        context.put("artifactId", artifact.getId());
        context.put("role", artifact.getRole().name());
        context.put("kind", artifact.getKind());
        context.put("sizeBytes", artifact.getSizeBytes());
        context.put("application", execution.getConfigApplication());
        context.put("snowGroup", execution.getConfigSnowGroup());
        context.put("agent", execution.getConfigAgent());
        auditLogger.logAtomic(
                actor.user(),
                AuditActionType.integration_artifact_register,
                request.getReleaseFlow().getId(),
                request.getId(),
                execution.getTask().getId(),
                context);
    }

    private ArtifactContent contentOf(IntegrationArtifact artifact) {
        IntegrationArtifact source = artifact.getStorageMode() == ArtifactStorageMode.REFERENCE
                ? artifact.getReferenceArtifact()
                : artifact;
        if (source == null) {
            throw IntegrationApiException.notFound("ARTIFACT_NOT_FOUND", "Artifact");
        }
        assertContentAvailable(source);
        return new ArtifactContent(
                artifact.getName(),
                artifact.getMediaType(),
                artifact.getSha256(),
                source.getContent());
    }

    private static void assertContentAvailable(IntegrationArtifact artifact) {
        if (artifact.getContent() == null || artifact.getContentPurgedAt() != null) {
            throw IntegrationApiException.notFound(
                    "ARTIFACT_CONTENT_NOT_AVAILABLE", "Artifact content");
        }
    }

    private void extendContentRetention(IntegrationArtifact artifact) {
        Instant renewedExpiry = retentionService.contentExpiry();
        if (artifact.getContentExpiresAt() == null
                || artifact.getContentExpiresAt().isBefore(renewedExpiry)) {
            artifact.setContentExpiresAt(renewedExpiry);
        }
    }

    private TaskInputArtifactApproval deniedOrHiddenInput(
            String artifactId,
            IntegrationActor actor
    ) {
        IntegrationArtifact artifact = artifactRepository.findWithProvenance(artifactId).orElse(null);
        if (artifact != null) {
            try {
                authorizationService.assertExecutionVisible(artifact.getExecution(), actor);
                throw new IntegrationApiException(
                        org.springframework.http.HttpStatus.FORBIDDEN,
                        "INPUT_ARTIFACT_NOT_APPROVED",
                        "The Artifact is not approved for this Task.",
                        false);
            } catch (IntegrationApiException exception) {
                if ("INPUT_ARTIFACT_NOT_APPROVED".equals(exception.getCode())) {
                    throw exception;
                }
            }
        }
        throw IntegrationApiException.notFound("ARTIFACT_NOT_FOUND", "Artifact");
    }

    private static IntegrationApiException policy(String message) {
        return IntegrationApiException.unprocessable("ARTIFACT_POLICY_VIOLATION", message);
    }

    private static IntegrationApiException unsupportedMedia(String message) {
        return new IntegrationApiException(
                org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_MEDIA_TYPE",
                message,
                false);
    }

    public record ArtifactContent(String name, String mediaType, String sha256, byte[] content) {
        public ArtifactContent {
            content = content.clone();
        }
    }
}
