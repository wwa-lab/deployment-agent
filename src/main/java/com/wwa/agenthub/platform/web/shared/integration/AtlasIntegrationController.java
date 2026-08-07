package com.wwa.agenthub.platform.web.shared.integration;

import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.dto.integration.ArtifactUploadMetadata;
import com.wwa.agenthub.contracts.dto.integration.CancelExecutionRequest;
import com.wwa.agenthub.contracts.dto.integration.CapabilityUsageDto;
import com.wwa.agenthub.contracts.dto.integration.FailExecutionRequest;
import com.wwa.agenthub.contracts.dto.integration.ExternalArtifactRequest;
import com.wwa.agenthub.contracts.dto.integration.IntegrationArtifactDto;
import com.wwa.agenthub.contracts.dto.integration.IntegrationEnvelope;
import com.wwa.agenthub.contracts.dto.integration.IntegrationExecutionDto;
import com.wwa.agenthub.contracts.dto.integration.IntegrationProgressEventDto;
import com.wwa.agenthub.contracts.dto.integration.IntegrationReviewDto;
import com.wwa.agenthub.contracts.dto.integration.IntegrationTaskDto;
import com.wwa.agenthub.contracts.dto.integration.IntegrationTaskBindingRequest;
import com.wwa.agenthub.contracts.dto.integration.ProgressEventRequest;
import com.wwa.agenthub.contracts.dto.integration.ReviewSubmissionRequest;
import com.wwa.agenthub.contracts.dto.integration.RerunTaskRequest;
import com.wwa.agenthub.contracts.dto.integration.StartExecutionRequest;
import com.wwa.agenthub.contracts.dto.integration.SubmitExecutionRequest;
import com.wwa.agenthub.contracts.enums.IntegrationClientType;
import com.wwa.agenthub.platform.domain.integration.IntegrationApiException;
import com.wwa.agenthub.platform.domain.integration.artifact.IntegrationArtifactService;
import com.wwa.agenthub.platform.domain.integration.artifact.ArtifactUploadAdmissionService;
import com.wwa.agenthub.platform.domain.integration.binding.IntegrationTaskBindingService;
import com.wwa.agenthub.platform.domain.integration.auth.IntegrationActor;
import com.wwa.agenthub.platform.domain.integration.auth.IntegrationActorResolver;
import com.wwa.agenthub.platform.domain.integration.idempotency.IdempotentCommandService;
import com.wwa.agenthub.platform.domain.integration.auth.IntegrationClientProperties;
import com.wwa.agenthub.platform.domain.integration.lifecycle.ExecutionLifecycleService;
import com.wwa.agenthub.platform.domain.integration.review.IntegrationReviewService;
import com.wwa.agenthub.platform.domain.integration.telemetry.CapabilityUsageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@RestController
@RequestMapping("/api/v1/integration")
@RequiredArgsConstructor
@Validated
public class AtlasIntegrationController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final int MAX_CURSOR_OFFSET = 10_000;

    private final ExecutionLifecycleService lifecycleService;
    private final IntegrationTaskBindingService taskBindingService;
    private final IntegrationArtifactService artifactService;
    private final ArtifactUploadAdmissionService artifactUploadAdmissionService;
    private final IntegrationReviewService reviewService;
    private final CapabilityUsageService capabilityUsageService;
    private final IdempotentCommandService idempotentCommandService;
    private final IntegrationActorResolver actorResolver;
    private final IntegrationClientProperties integrationProperties;

    @GetMapping("/tasks")
    public ResponseEntity<IntegrationEnvelope.Page<IntegrationTaskDto>> listTasks(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String team,
            @RequestParam(required = false) String agentModuleId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) String cursor,
            @AuthenticationPrincipal UserContext user,
        HttpServletRequest request
    ) {
        IntegrationActor actor = actorResolver.resolve(user, request);
        ExecutionLifecycleService.TaskCursor position = decodeTaskCursor(cursor);
        ExecutionLifecycleService.TaskWindow window = lifecycleService.listTasks(
                actor,
                new ExecutionLifecycleService.TaskFilters(status, projectId, team, agentModuleId),
                position,
                limit);
        return ResponseEntity.ok(IntegrationEnvelope.Page.of(
                window.items(),
                window.hasMore() ? encodeTaskCursor(window.nextCursor()) : null,
                window.hasMore()));
    }

    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<IntegrationEnvelope.Success<IntegrationTaskDto>> getTask(
            @PathVariable String taskId,
            @AuthenticationPrincipal UserContext user,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(IntegrationEnvelope.Success.of(
                lifecycleService.getTask(taskId, actorResolver.resolve(user, request))));
    }

    @PutMapping("/admin/tasks/{taskId}/binding")
    public ResponseEntity<IntegrationEnvelope.Success<IntegrationTaskDto>> bindTask(
            @PathVariable String taskId,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @Valid @RequestBody IntegrationTaskBindingRequest body,
            @AuthenticationPrincipal UserContext user,
            HttpServletRequest request
    ) {
        IntegrationActor actor = actorResolver.resolve(user, request);
        taskBindingService.authorize(taskId, actor);
        return commandResponse(idempotentCommandService.execute(
                actor,
                "PUT",
                "/api/v1/integration/admin/tasks/" + taskId + "/binding",
                idempotencyKey,
                body,
                HttpStatus.OK.value(),
                IntegrationTaskDto.class,
                () -> taskBindingService.bind(taskId, body, actor)), ignored -> null);
    }

    @GetMapping("/tasks/{taskId}/executions")
    public ResponseEntity<IntegrationEnvelope.Page<IntegrationExecutionDto>> history(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
            @RequestParam(required = false) String cursor,
            @AuthenticationPrincipal UserContext user,
            HttpServletRequest request
    ) {
        ExecutionLifecycleService.ExecutionCursor position = decodeExecutionCursor(cursor);
        ExecutionLifecycleService.ExecutionWindow window = lifecycleService.history(
                taskId, actorResolver.resolve(user, request), position, limit);
        return ResponseEntity.ok(IntegrationEnvelope.Page.of(
                window.items(),
                window.hasMore() ? encodeExecutionCursor(window.nextCursor()) : null,
                window.hasMore()));
    }

    @GetMapping("/tasks/{taskId}/approved-input-artifacts")
    public ResponseEntity<IntegrationEnvelope.Page<IntegrationArtifactDto>> inputArtifacts(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) String cursor,
            @AuthenticationPrincipal UserContext user,
            HttpServletRequest request
    ) {
        List<IntegrationArtifactDto> artifacts = artifactService.listApprovedInputs(
                taskId, actorResolver.resolve(user, request));
        return ResponseEntity.ok(page(artifacts, limit, cursor));
    }

    @PostMapping("/admin/tasks/{taskId}/approved-input-artifacts/{artifactId}")
    public ResponseEntity<IntegrationEnvelope.Success<IntegrationArtifactDto>> approveInputArtifact(
            @PathVariable String taskId,
            @PathVariable String artifactId,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @AuthenticationPrincipal UserContext user,
            HttpServletRequest request
    ) {
        IntegrationActor actor = actorResolver.resolve(user, request);
        artifactService.authorizeApproveInput(taskId, artifactId, actor);
        return commandResponse(idempotentCommandService.execute(
                actor,
                "POST",
                "/api/v1/integration/admin/tasks/" + taskId
                        + "/approved-input-artifacts/" + artifactId,
                idempotencyKey,
                Map.of("taskId", taskId, "artifactId", artifactId),
                HttpStatus.OK.value(),
                IntegrationArtifactDto.class,
                () -> artifactService.approveInput(taskId, artifactId, actor)), ignored -> null);
    }

    @GetMapping("/tasks/{taskId}/approved-input-artifacts/{artifactId}/content")
    public ResponseEntity<byte[]> downloadInputArtifact(
            @PathVariable String taskId,
            @PathVariable String artifactId,
            @AuthenticationPrincipal UserContext user,
            HttpServletRequest request
    ) {
        return download(artifactId, artifactService.downloadApprovedInput(
                taskId, artifactId, actorResolver.resolve(user, request)));
    }

    @PostMapping("/tasks/{taskId}/executions")
    public ResponseEntity<IntegrationEnvelope.Success<IntegrationExecutionDto>> start(
            @PathVariable String taskId,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @Valid @RequestBody StartExecutionRequest body,
            @AuthenticationPrincipal UserContext user,
            HttpServletRequest request
    ) {
        IntegrationActor actor = actorResolver.resolve(user, request);
        lifecycleService.authorizeStart(taskId, actor);
        String path = "/api/v1/integration/tasks/" + taskId + "/executions";
        IdempotentCommandService.Result<IntegrationExecutionDto> result = idempotentCommandService.execute(
                actor,
                "POST",
                path,
                idempotencyKey,
                body,
                HttpStatus.CREATED.value(),
                IntegrationExecutionDto.class,
                () -> lifecycleService.authorizeStartReplay(taskId, actor),
                () -> lifecycleService.start(taskId, body, actor));
        return commandResponse(result, dto -> "/api/v1/integration/executions/" + dto.executionId());
    }

    @GetMapping("/executions/{executionId}")
    public ResponseEntity<IntegrationEnvelope.Success<IntegrationExecutionDto>> getExecution(
            @PathVariable String executionId,
            @AuthenticationPrincipal UserContext user,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(IntegrationEnvelope.Success.of(
                lifecycleService.getExecution(executionId, actorResolver.resolve(user, request))));
    }

    @PostMapping("/tasks/{taskId}/rerun")
    public ResponseEntity<IntegrationEnvelope.Success<IntegrationTaskDto>> rerun(
            @PathVariable String taskId,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @Valid @RequestBody RerunTaskRequest body,
            @AuthenticationPrincipal UserContext user,
            HttpServletRequest request
    ) {
        IntegrationActor actor = actorResolver.resolve(user, request);
        lifecycleService.authorizeRerun(taskId, actor);
        return commandResponse(idempotentCommandService.execute(
                actor,
                "POST",
                "/api/v1/integration/tasks/" + taskId + "/rerun",
                idempotencyKey,
                body,
                HttpStatus.OK.value(),
                IntegrationTaskDto.class,
                () -> lifecycleService.authorizeRerunReplay(taskId, body.executionId(), actor),
                () -> lifecycleService.rerun(taskId, body, actor)), ignored -> null);
    }

    @PostMapping("/executions/{executionId}/progress-events")
    public ResponseEntity<IntegrationEnvelope.Success<IntegrationProgressEventDto>> progress(
            @PathVariable String executionId,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @Valid @RequestBody ProgressEventRequest body,
            @AuthenticationPrincipal UserContext user,
            HttpServletRequest request
    ) {
        IntegrationActor actor = actorResolver.resolve(user, request);
        lifecycleService.authorizeWrite(executionId, actor);
        return commandResponse(idempotentCommandService.execute(
                actor,
                "POST",
                executionPath(executionId, "/progress-events"),
                idempotencyKey,
                body,
                HttpStatus.CREATED.value(),
                IntegrationProgressEventDto.class,
                () -> lifecycleService.authorizeWriteReplay(executionId, actor),
                () -> lifecycleService.progress(executionId, body, actor)), ignored -> null);
    }

    @PostMapping(value = "/executions/{executionId}/artifacts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IntegrationEnvelope.Success<IntegrationArtifactDto>> uploadArtifact(
            @PathVariable String executionId,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @Valid @RequestPart("metadata") ArtifactUploadMetadata metadata,
            @RequestPart("content") MultipartFile contentPart,
            @AuthenticationPrincipal UserContext user,
            HttpServletRequest request
    ) {
        IntegrationActor actor = actorResolver.resolve(user, request);
        lifecycleService.authorizeWrite(executionId, actor);
        return artifactUploadAdmissionService.execute(
                executionId,
                () -> {
                    byte[] content = read(contentPart, integrationProperties.getMaxArtifactBytes());
                    ArtifactFingerprint fingerprint = new ArtifactFingerprint(metadata, digest(content));
                    return commandResponse(idempotentCommandService.execute(
                            actor,
                            "POST",
                            executionPath(executionId, "/artifacts"),
                            idempotencyKey,
                            fingerprint,
                            HttpStatus.CREATED.value(),
                            IntegrationArtifactDto.class,
                            () -> lifecycleService.authorizeWriteReplay(executionId, actor),
                            () -> artifactService.upload(executionId, metadata, content, actor)),
                            dto -> executionPath(executionId, "/artifacts/" + dto.artifactId()));
                });
    }

    @PostMapping(
            value = "/executions/{executionId}/artifacts",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IntegrationEnvelope.Success<IntegrationArtifactDto>> referenceArtifact(
            @PathVariable String executionId,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @Valid @RequestBody ExternalArtifactRequest body,
            @AuthenticationPrincipal UserContext user,
            HttpServletRequest request
    ) {
        IntegrationActor actor = actorResolver.resolve(user, request);
        lifecycleService.authorizeWrite(executionId, actor);
        return commandResponse(idempotentCommandService.execute(
                actor,
                "POST",
                executionPath(executionId, "/artifacts"),
                idempotencyKey,
                body,
                HttpStatus.CREATED.value(),
                IntegrationArtifactDto.class,
                () -> lifecycleService.authorizeWriteReplay(executionId, actor),
                () -> artifactService.reference(executionId, body, actor)),
                dto -> executionPath(executionId, "/artifacts/" + dto.artifactId()));
    }

    @GetMapping("/executions/{executionId}/artifacts")
    public ResponseEntity<IntegrationEnvelope.Success<List<IntegrationArtifactDto>>> listArtifacts(
            @PathVariable String executionId,
            @AuthenticationPrincipal UserContext user,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(IntegrationEnvelope.Success.of(
                artifactService.list(executionId, actorResolver.resolve(user, request))));
    }

    @GetMapping("/executions/{executionId}/artifacts/{artifactId}/content")
    public ResponseEntity<byte[]> downloadArtifact(
            @PathVariable String executionId,
            @PathVariable String artifactId,
            @AuthenticationPrincipal UserContext user,
            HttpServletRequest request
    ) {
        return download(artifactId, artifactService.download(
                executionId, artifactId, actorResolver.resolve(user, request)));
    }

    @PostMapping("/executions/{executionId}/submit")
    public ResponseEntity<IntegrationEnvelope.Success<IntegrationExecutionDto>> submit(
            @PathVariable String executionId,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @Valid @RequestBody SubmitExecutionRequest body,
            @AuthenticationPrincipal UserContext user,
            HttpServletRequest request
    ) {
        IntegrationActor actor = actorResolver.resolve(user, request);
        lifecycleService.authorizeWrite(executionId, actor);
        return executionCommand(
                executionId, "/submit", idempotencyKey, body, actor,
                () -> lifecycleService.submit(executionId, body, actor));
    }

    @PostMapping("/executions/{executionId}/fail")
    public ResponseEntity<IntegrationEnvelope.Success<IntegrationExecutionDto>> fail(
            @PathVariable String executionId,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @Valid @RequestBody FailExecutionRequest body,
            @AuthenticationPrincipal UserContext user,
            HttpServletRequest request
    ) {
        IntegrationActor actor = actorResolver.resolve(user, request);
        lifecycleService.authorizeWrite(executionId, actor);
        return executionCommand(
                executionId, "/fail", idempotencyKey, body, actor,
                () -> lifecycleService.fail(executionId, body, actor));
    }

    @PostMapping("/executions/{executionId}/cancel")
    public ResponseEntity<IntegrationEnvelope.Success<IntegrationExecutionDto>> cancel(
            @PathVariable String executionId,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @Valid @RequestBody CancelExecutionRequest body,
            @AuthenticationPrincipal UserContext user,
            HttpServletRequest request
    ) {
        IntegrationActor actor = actorResolver.resolve(user, request);
        lifecycleService.authorizeWrite(executionId, actor);
        return executionCommand(
                executionId, "/cancel", idempotencyKey, body, actor,
                () -> lifecycleService.cancel(executionId, body, actor));
    }

    @GetMapping("/executions/{executionId}/review-decision")
    public ResponseEntity<IntegrationEnvelope.Success<IntegrationReviewDto>> getReview(
            @PathVariable String executionId,
            @AuthenticationPrincipal UserContext user,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(IntegrationEnvelope.Success.of(
                reviewService.get(executionId, actorResolver.resolve(user, request))));
    }

    @PostMapping("/executions/{executionId}/review-decision")
    public ResponseEntity<IntegrationEnvelope.Success<IntegrationReviewDto>> review(
            @PathVariable String executionId,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @Valid @RequestBody ReviewSubmissionRequest body,
            @AuthenticationPrincipal UserContext user,
            HttpServletRequest request
    ) {
        IntegrationActor actor = actorResolver.resolve(user, request);
        reviewService.authorizeSubmit(executionId, actor);
        return commandResponse(idempotentCommandService.execute(
                actor,
                "POST",
                executionPath(executionId, "/review-decision"),
                idempotencyKey,
                body,
                HttpStatus.OK.value(),
                IntegrationReviewDto.class,
                () -> lifecycleService.authorizeReviewReplay(executionId, actor),
                () -> reviewService.submit(executionId, body, actor)), ignored -> null);
    }

    @GetMapping("/telemetry/capability-usage")
    public ResponseEntity<IntegrationEnvelope.Success<CapabilityUsageDto>> capabilityUsage(
            @RequestParam(required = false) String capabilityId,
            @RequestParam(required = false) String skillId,
            @RequestParam(required = false) String team,
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String agent,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) IntegrationClientType clientType,
            @AuthenticationPrincipal UserContext user,
            HttpServletRequest request
    ) {
        IntegrationActor actor = actorResolver.resolve(user, request);
        CapabilityUsageDto.Filters filters = new CapabilityUsageDto.Filters(
                capabilityId, skillId, team, projectId, agent, from, to, clientType);
        return ResponseEntity.ok(IntegrationEnvelope.Success.of(
                capabilityUsageService.aggregate(filters, actor)));
    }

    private ResponseEntity<IntegrationEnvelope.Success<IntegrationExecutionDto>> executionCommand(
            String executionId,
            String suffix,
            String key,
            Object fingerprint,
            IntegrationActor actor,
            java.util.function.Supplier<IntegrationExecutionDto> command
    ) {
        return commandResponse(idempotentCommandService.execute(
                actor,
                "POST",
                executionPath(executionId, suffix),
                key,
                fingerprint,
                HttpStatus.OK.value(),
                IntegrationExecutionDto.class,
                () -> lifecycleService.authorizeWriteReplay(executionId, actor),
                command), ignored -> null);
    }

    private static <T> ResponseEntity<IntegrationEnvelope.Success<T>> commandResponse(
            IdempotentCommandService.Result<T> result,
            Function<T, String> location
    ) {
        HttpHeaders headers = new HttpHeaders();
        if (result.replayed()) {
            headers.set("Idempotency-Replayed", "true");
        }
        String resourceLocation = location.apply(result.body());
        if (resourceLocation != null) {
            headers.set(HttpHeaders.LOCATION, resourceLocation);
        }
        return new ResponseEntity<>(
                IntegrationEnvelope.Success.of(result.body()),
                headers,
                result.responseStatus());
    }

    private static ResponseEntity<byte[]> download(
            String artifactId,
            IntegrationArtifactService.ArtifactContent content
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(content.mediaType()));
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"atlas-artifact-" + safeFilename(artifactId) + "\"");
        headers.setCacheControl("private, no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Content-Digest", "sha-256=:" + Base64.getEncoder().encodeToString(
                java.util.HexFormat.of().parseHex(content.sha256())) + ":");
        return new ResponseEntity<>(content.content(), headers, HttpStatus.OK);
    }

    private static <T> IntegrationEnvelope.Page<T> page(List<T> values, int limit, String cursor) {
        int offset = decodeCursor(cursor);
        if (offset > values.size()) {
            throw IntegrationApiException.badRequest("INVALID_REQUEST", "Cursor is outside the result set.");
        }
        int end = Math.min(values.size(), offset + limit);
        List<T> data = values.subList(offset, end);
        boolean hasMore = end < values.size();
        return IntegrationEnvelope.Page.of(data, hasMore ? encodeCursor(end) : null, hasMore);
    }

    private static int decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(cursor), StandardCharsets.US_ASCII);
            int offset = Integer.parseInt(decoded);
            if (offset < 0 || offset > MAX_CURSOR_OFFSET) {
                throw new NumberFormatException();
            }
            return offset;
        } catch (IllegalArgumentException exception) {
            throw IntegrationApiException.badRequest("INVALID_REQUEST", "Cursor is invalid.");
        }
    }

    private static ExecutionLifecycleService.TaskCursor decodeTaskCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(cursor), StandardCharsets.US_ASCII);
            int separator = decoded.indexOf('\n');
            if (separator <= 0 || separator == decoded.length() - 1) {
                throw new IllegalArgumentException();
            }
            java.time.Instant createdAt = java.time.Instant.parse(decoded.substring(0, separator));
            String taskId = decoded.substring(separator + 1);
            if (taskId.length() > 128 || taskId.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException();
            }
            return new ExecutionLifecycleService.TaskCursor(createdAt, taskId);
        } catch (RuntimeException exception) {
            throw IntegrationApiException.badRequest("INVALID_REQUEST", "Cursor is invalid.");
        }
    }

    private static String encodeTaskCursor(ExecutionLifecycleService.TaskCursor cursor) {
        String value = cursor.createdAt() + "\n" + cursor.taskId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                value.getBytes(StandardCharsets.US_ASCII));
    }

    private static ExecutionLifecycleService.ExecutionCursor decodeExecutionCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(cursor), StandardCharsets.US_ASCII);
            int separator = decoded.indexOf('\n');
            if (separator <= 0 || separator == decoded.length() - 1) {
                throw new IllegalArgumentException();
            }
            int attemptNumber = Integer.parseInt(decoded.substring(0, separator));
            String executionId = decoded.substring(separator + 1);
            if (executionId.length() > 128
                    || executionId.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException();
            }
            return new ExecutionLifecycleService.ExecutionCursor(attemptNumber, executionId);
        } catch (RuntimeException exception) {
            throw IntegrationApiException.badRequest("INVALID_REQUEST", "Cursor is invalid.");
        }
    }

    private static String encodeExecutionCursor(ExecutionLifecycleService.ExecutionCursor cursor) {
        String value = cursor.attemptNumber() + "\n" + cursor.executionId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                value.getBytes(StandardCharsets.US_ASCII));
    }

    private static String encodeCursor(int offset) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                Integer.toString(offset).getBytes(StandardCharsets.US_ASCII));
    }

    private static String executionPath(String executionId, String suffix) {
        return "/api/v1/integration/executions/" + executionId + suffix;
    }

    private static byte[] read(MultipartFile file, long maximumBytes) {
        if (file.getSize() > maximumBytes) {
            throw new IntegrationApiException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "ARTIFACT_TOO_LARGE",
                    "The Artifact exceeds the configured size limit.",
                    false);
        }
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw IntegrationApiException.badRequest(
                    "ARTIFACT_POLICY_VIOLATION",
                    "Artifact content could not be read.");
        }
    }

    private static String digest(byte[] content) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String safeFilename(String value) {
        return value.replaceAll("[^A-Za-z0-9._ -]", "_").replace("\"", "_");
    }

    private record ArtifactFingerprint(ArtifactUploadMetadata metadata, String actualSha256) {
    }

}
