package com.wwa.deploymentagent.domain.fileimport;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.enums.AuditActionType;
import com.wwa.deploymentagent.contracts.enums.RequestStatus;
import com.wwa.deploymentagent.contracts.enums.TaskStatus;
import com.wwa.deploymentagent.agents.deployment.domain.ReleaseFlowFamilyKey;
import com.wwa.deploymentagent.domain.audit.AuditLoggerService;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlow;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlowRepository;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlowService;
import com.wwa.deploymentagent.domain.releaseflow.Request;
import com.wwa.deploymentagent.domain.releaseflow.RequestRepository;
import com.wwa.deploymentagent.domain.task.Task;
import com.wwa.deploymentagent.domain.task.TaskRepository;
import com.wwa.deploymentagent.errors.ImportValidationException;
import com.wwa.deploymentagent.errors.ValidationAppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * ImportService – orchestrates the full Excel import transaction.
 *
 * <ol>
 *   <li>Delegates parsing and validation to {@link ExcelParserService}</li>
 *   <li>Groups parsed rows by project_id</li>
 *   <li>Finds or creates a Release Flow per project/stage upload</li>
 *   <li>Finds or creates a Request for the upload stage</li>
 *   <li>Creates tasks for the selected rundown</li>
 *   <li>Audits the upload event</li>
 * </ol>
 *
 * The entire import runs in a single transaction; any error rolls back all changes.
 */
@Service
@RequiredArgsConstructor
public class ImportService {

    private final ExcelParserService excelParserService;
    private final ReleaseFlowRepository releaseFlowRepository;
    private final ReleaseFlowService releaseFlowService;
    private final RequestRepository requestRepository;
    private final TaskRepository taskRepository;
    private final AuditLoggerService auditLogger;

    @Transactional
    public ImportResult importFile(byte[] fileBytes, String stage, UserContext user) throws IOException {
        return importFile(fileBytes, stage, user, null, null, null, null);
    }

    @Transactional
    public ImportResult importFile(
            byte[] fileBytes,
            String stage,
            UserContext user,
            String snowGroup,
            String application,
            String agent) throws IOException {
        return importFile(fileBytes, stage, user, null, snowGroup, application, agent);
    }

    @Transactional
    public ImportResult importFile(
            byte[] fileBytes,
            String stage,
            UserContext user,
            String requestedReleaseId,
            String snowGroup,
            String application,
            String agent) throws IOException {
        ParseResult parsed = excelParserService.parse(fileBytes);
        if (parsed.hasErrors()) {
            throw new ImportValidationException(parsed.errors());
        }
        if (parsed.rows().isEmpty()) {
            throw new ImportValidationException(
                    List.of(new ImportError(0, "Sheet", "No data rows found in sheet")));
        }

        // Group rows by projectId (one file may contain multiple projects)
        Map<String, List<ParsedTaskRow>> byProject = parsed.rows().stream()
                .collect(Collectors.groupingBy(ParsedTaskRow::projectId));

        String lastReleaseFlowId = null;
        String lastReleaseId     = null;
        int totalTaskCount       = 0;

        for (Map.Entry<String, List<ParsedTaskRow>> entry : byProject.entrySet()) {
            String projectId   = entry.getKey();
            List<ParsedTaskRow> rows = entry.getValue();
            String projectName = rows.get(0).projectName();
            String requestOwner = inferRequestOwner(rows, user);

            ReleaseFlow rf = findOrCreateReleaseFlow(projectId, projectName, stage, requestedReleaseId);

            Request request = createRequestAttempt(rf, stage, user, snowGroup, application, agent, requestOwner);

            for (ParsedTaskRow row : rows) {
                upsertTask(request, row);
                totalTaskCount++;
            }

            lastReleaseFlowId = rf.getId();
            lastReleaseId     = rf.getReleaseId();
        }

        auditLogger.log(user, AuditActionType.upload, lastReleaseFlowId, null, null,
                Map.of(
                        "stage", stage,
                        "taskCount", totalTaskCount,
                        "snowGroup", normalizeBlank(snowGroup) != null ? normalizeBlank(snowGroup) : "",
                        "application", normalizeBlank(application) != null ? normalizeBlank(application) : "",
                        "agent", normalizeBlank(agent) != null ? normalizeBlank(agent) : ""));

        return new ImportResult(
                lastReleaseFlowId,
                lastReleaseId,
                stage,
                totalTaskCount,
                normalizeBlank(snowGroup),
                normalizeBlank(application),
                normalizeBlank(agent));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private ReleaseFlow findOrCreateReleaseFlow(
            String projectId,
            String projectName,
            String stage,
            String requestedReleaseId) {
        String explicitReleaseId = normalizeBlank(requestedReleaseId);
        if (explicitReleaseId != null) {
            return findOrCreateReleaseFlowByIdentifier(projectId, projectName, stage, explicitReleaseId);
        }

        return createReleaseFlow(projectId, projectName, stage);
    }

    private ReleaseFlow findOrCreateReleaseFlowByIdentifier(
            String projectId,
            String projectName,
            String stage,
            String explicitReleaseId) {
        String normalizedReleaseId = normalizeReleaseIdentifier(explicitReleaseId);
        Optional<ReleaseFlow> existing = findExistingReleaseFlowByIdentifier(
                projectId,
                explicitReleaseId,
                normalizedReleaseId);

        if (existing.isPresent()) {
            ReleaseFlow releaseFlow = existing.get();
            if (releaseFlow.getArchivedAt() != null) {
                throw new ValidationAppException(
                        "Release identifier '" + explicitReleaseId + "' is already archived for project '"
                                + projectId + "'. Restore that rundown or use a new release identifier.");
            }
            return releaseFlow;
        }

        return releaseFlowService.create(projectId, projectName, explicitReleaseId, normalizedReleaseId, stage);
    }

    private ReleaseFlow createReleaseFlow(String projectId, String projectName, String stage) {
        String normalized    = normalizeId(projectId);
        long count           = releaseFlowRepository.countByProjectId(projectId);
        String genReleaseId  = stage.toLowerCase() + "-" + normalized
                + "-" + String.format("%04d", count + 1);
        return releaseFlowService.create(
                projectId,
                projectName,
                genReleaseId,
                normalizeReleaseIdentifier(genReleaseId),
                stage);
    }

    private Request createRequestAttempt(
            ReleaseFlow rf,
            String stage,
            UserContext user,
            String snowGroup,
            String application,
            String agent,
            String owner) {
        Request latestAttempt = requestRepository
                .findTopByReleaseFlowIdAndStageOrderByAttemptNumberDescUpdatedAtDesc(rf.getId(), stage)
                .orElse(null);

        int nextAttempt = requestRepository.findMaxAttemptNumberByReleaseFlowIdAndStage(rf.getId(), stage) + 1;

        Request request = new Request();
        request.setReleaseFlow(rf);
        request.setStage(stage);
        request.setAttemptNumber(nextAttempt);
        request.setRequestStatus(RequestStatus.Pending);
        request.setCreatedBy(user.userId());
        request.setArchivedAt(null);
        request.setArchivedBy(null);
        request.setSnowGroup(coalesceScopeValue(
                snowGroup,
                latestAttempt != null ? latestAttempt.getSnowGroup() : null));
        request.setApplication(coalesceScopeValue(
                application,
                latestAttempt != null ? latestAttempt.getApplication() : null,
                rf.getProjectName()));
        request.setAgent(coalesceScopeValue(
                agent,
                latestAttempt != null ? latestAttempt.getAgent() : null));
        request.setOwner(coalesceScopeValue(
                owner,
                latestAttempt != null ? latestAttempt.getOwner() : null));
        return requestRepository.save(request);
    }

    private void upsertTask(Request request, ParsedTaskRow row) {
        Task task = new Task();
        task.setRequest(request);
        task.setTaskStatus(TaskStatus.Pending);

        // Apply all template-derived fields for this newly created rundown task.
        task.setTaskGroupId(row.taskGroupId());
        task.setTaskGroupName(row.taskGroupName());
        task.setStepSeq(row.stepSeq());
        task.setTaskName(row.taskName());
        task.setExecutionType(row.executionType());
        task.setCritical(row.critical());
        task.setInputParameters(row.inputParameters());
        task.setExpectedOutput(row.expectedOutput());
        task.setOwner(row.owner());
        task.setPlannedStartTime(row.plannedStartTime());
        task.setPlannedEndTime(row.plannedEndTime());
        task.setImportMetadata(row.importMetadata());

        taskRepository.save(task);
    }

    private static String normalizeId(String id) {
        return id.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private String normalizeReleaseIdentifier(String releaseIdentifier) {
        String normalizedReleaseIdentifier = ReleaseFlowFamilyKey.fromIdentifier(releaseIdentifier);
        if (normalizedReleaseIdentifier.isBlank()) {
            throw new ValidationAppException("Release identifier must contain at least one letter or number.");
        }
        if (releaseIdentifier.length() > 255) {
            throw new ValidationAppException("Release identifier cannot exceed 255 characters.");
        }
        return normalizedReleaseIdentifier;
    }

    private Optional<ReleaseFlow> findExistingReleaseFlowByIdentifier(
            String projectId,
            String explicitReleaseId,
            String normalizedReleaseId) {
        Optional<ReleaseFlow> existing = releaseFlowRepository
                .findByProjectIdAndNormalizedReleaseId(projectId, normalizedReleaseId);
        if (existing.isPresent()) {
            return existing;
        }

        String legacyNormalizedReleaseId = explicitReleaseId.trim().toLowerCase();
        if (legacyNormalizedReleaseId.equals(normalizedReleaseId)) {
            return releaseFlowRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtDesc(projectId).stream()
                    .filter(releaseFlow -> ReleaseFlowFamilyKey.fromStoredRelease(
                            releaseFlow.getReleaseId(),
                            releaseFlow.getNormalizedReleaseId()).equals(normalizedReleaseId))
                    .findFirst();
        }

        Optional<ReleaseFlow> legacy = releaseFlowRepository.findByProjectIdAndNormalizedReleaseId(
                projectId,
                legacyNormalizedReleaseId);
        if (legacy.isPresent()) {
            return legacy;
        }

        return releaseFlowRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtDesc(projectId).stream()
                .filter(releaseFlow -> ReleaseFlowFamilyKey.fromStoredRelease(
                        releaseFlow.getReleaseId(),
                        releaseFlow.getNormalizedReleaseId()).equals(normalizedReleaseId))
                .findFirst();
    }

    private String inferRequestOwner(List<ParsedTaskRow> rows, UserContext user) {
        List<String> uniqueOwners = rows.stream()
                .map(ParsedTaskRow::owner)
                .map(this::normalizeBlank)
                .filter(value -> value != null)
                .distinct()
                .toList();

        if (uniqueOwners.size() == 1) {
            return uniqueOwners.get(0);
        }

        return normalizeBlank(user.displayName()) != null ? normalizeBlank(user.displayName()) : user.userId();
    }

    private String coalesceScopeValue(String preferred, String existing) {
        String normalizedPreferred = normalizeBlank(preferred);
        return normalizedPreferred != null ? normalizedPreferred : normalizeBlank(existing);
    }

    private String coalesceScopeValue(String preferred, String existing, String fallback) {
        String normalizedPreferred = normalizeBlank(preferred);
        if (normalizedPreferred != null) {
            return normalizedPreferred;
        }
        String normalizedExisting = normalizeBlank(existing);
        if (normalizedExisting != null) {
            return normalizedExisting;
        }
        return normalizeBlank(fallback);
    }

    private String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
