package com.wwa.deploymentagent.domain.releaseflow;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.dto.CreateRundownFromTemplateDto;
import com.wwa.deploymentagent.contracts.dto.CreateRundownFromTemplateTaskDto;
import com.wwa.deploymentagent.contracts.enums.AuditActionType;
import com.wwa.deploymentagent.contracts.enums.Stage;
import com.wwa.deploymentagent.contracts.enums.TaskStatus;
import com.wwa.deploymentagent.domain.audit.AuditLoggerService;
import com.wwa.deploymentagent.domain.fileimport.ImportResult;
import com.wwa.deploymentagent.domain.task.Task;
import com.wwa.deploymentagent.domain.task.TaskRepository;
import com.wwa.deploymentagent.errors.ValidationAppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class TemplateRundownCreationService {
    private static final Pattern RELEASE_IDENTIFIER_PATTERN = Pattern.compile(
            "^(?<prefix>[a-z0-9]+(?:-[a-z0-9]+)*)-(?<stage>sit|uat|prod)-(?<sequence>0[1-9]|[1-9][0-9])$",
            Pattern.CASE_INSENSITIVE);

    private final ReleaseFlowRepository releaseFlowRepository;
    private final ReleaseFlowService releaseFlowService;
    private final RequestRepository requestRepository;
    private final TaskRepository taskRepository;
    private final AuditLoggerService auditLogger;

    @Transactional
    public ImportResult createRundown(CreateRundownFromTemplateDto draft, UserContext user) {
        if (draft == null) {
            throw new ValidationAppException("Template rundown payload is required.");
        }

        String projectName = requireValue(draft.projectName(), "Project name is required.");
        Stage stage = requireStage(draft.stage());
        List<CreateRundownFromTemplateTaskDto> tasks = normalizeTasks(draft.tasks());
        String releaseIdentifier = validateReleaseIdentifier(draft.releaseId(), stage);

        String projectId = normalizeBlank(draft.projectId());
        if (projectId == null) {
            projectId = deriveProjectId(projectName);
        }

        String requestOwner = resolveRequestOwner(draft.owner(), tasks, user);
        Integer estimatedRemainingMinutes = resolveEstimatedRemainingMinutes(
                draft.estimatedRemainingMinutes(),
                tasks);

        ReleaseFlow releaseFlow = findOrCreateReleaseFlowByIdentifier(
                projectId,
                projectName,
                stage,
                releaseIdentifier);
        Request request = createRequestAttempt(
                releaseFlow,
                stage,
                user,
                draft.snowGroup(),
                draft.application(),
                draft.agent(),
                requestOwner,
                draft.site(),
                estimatedRemainingMinutes);

        for (int index = 0; index < tasks.size(); index++) {
            createTask(request, tasks.get(index), index);
        }

        auditLogger.log(
                user,
                AuditActionType.upload,
                releaseFlow.getId(),
                request.getId(),
                null,
                buildAuditContext(draft, stage, tasks.size()));

        return new ImportResult(
                releaseFlow.getId(),
                releaseFlow.getReleaseId(),
                stage,
                tasks.size(),
                normalizeBlank(draft.snowGroup()),
                normalizeBlank(draft.application()),
                normalizeBlank(draft.agent()));
    }

    private ReleaseFlow findOrCreateReleaseFlowByIdentifier(
            String projectId,
            String projectName,
            Stage stage,
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
            return existing.get();
        }

        return releaseFlowService.create(projectId, projectName, explicitReleaseId, normalizedReleaseId, stage);
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

    private Request createRequestAttempt(
            ReleaseFlow releaseFlow,
            Stage stage,
            UserContext user,
            String snowGroup,
            String application,
            String agent,
            String owner,
            String site,
            Integer estimatedRemainingMinutes) {
        Request latestAttempt = requestRepository
                .findTopByReleaseFlowIdAndStageOrderByAttemptNumberDescUpdatedAtDesc(releaseFlow.getId(), stage)
                .orElse(null);

        int nextAttempt = requestRepository.findMaxAttemptNumberByReleaseFlowIdAndStage(releaseFlow.getId(), stage) + 1;

        Request request = new Request();
        request.setReleaseFlow(releaseFlow);
        request.setStage(stage);
        request.setAttemptNumber(nextAttempt);
        request.setRequestStatus(com.wwa.deploymentagent.contracts.enums.RequestStatus.Pending);
        request.setCreatedBy(user.userId());
        request.setArchivedAt(null);
        request.setArchivedBy(null);
        request.setSnowGroup(coalesceScopeValue(
                snowGroup,
                latestAttempt != null ? latestAttempt.getSnowGroup() : null));
        request.setApplication(coalesceScopeValue(
                application,
                latestAttempt != null ? latestAttempt.getApplication() : null,
                releaseFlow.getProjectName()));
        request.setAgent(coalesceScopeValue(
                agent,
                latestAttempt != null ? latestAttempt.getAgent() : null));
        request.setOwner(coalesceScopeValue(
                owner,
                latestAttempt != null ? latestAttempt.getOwner() : null));
        request.setSite(coalesceScopeValue(
                site,
                latestAttempt != null ? latestAttempt.getSite() : null));
        request.setEstimatedRemainingMinutes(coalesceInt(
                estimatedRemainingMinutes,
                latestAttempt != null ? latestAttempt.getEstimatedRemainingMinutes() : null));
        return requestRepository.save(request);
    }

    private void createTask(Request request, CreateRundownFromTemplateTaskDto draftTask, int index) {
        Task task = new Task();
        task.setRequest(request);
        task.setTaskStatus(TaskStatus.Pending);
        task.setTaskGroupId(String.format("TG-%03d", index + 1));
        task.setTaskGroupName(requireValue(draftTask.taskName(), "Task name is required."));
        task.setStepSeq(draftTask.step());
        task.setTaskName(requireValue(draftTask.stepName(), "Task step name is required."));
        task.setExecutionType(draftTask.type());
        task.setCritical(Boolean.TRUE.equals(draftTask.critical()));
        task.setInputParameters(Map.of("script", "", "parameters", ""));
        task.setExpectedOutput(null);
        task.setOwner(normalizeBlank(draftTask.owner()));
        task.setImportMetadata(buildImportMetadata(draftTask));
        request.getTasks().add(task);
        taskRepository.save(task);
    }

    private Map<String, Object> buildImportMetadata(CreateRundownFromTemplateTaskDto draftTask) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        String category = normalizeBlank(draftTask.category());
        String dependencies = normalizeBlank(draftTask.dependencies());

        if (category != null) {
            metadata.put("activity_category", category);
        }
        if (dependencies != null) {
            metadata.put("dependencies", dependencies);
        }

        return metadata.isEmpty() ? null : metadata;
    }

    private Map<String, Object> buildAuditContext(
            CreateRundownFromTemplateDto draft,
            Stage stage,
            int taskCount) {
        Map<String, Object> auditContext = new LinkedHashMap<>();
        auditContext.put("stage", stage.name());
        auditContext.put("taskCount", taskCount);
        auditContext.put("source", "template");
        auditContext.put("templateId", normalizeBlank(draft.templateId()) != null ? normalizeBlank(draft.templateId()) : "");
        auditContext.put("templateName", normalizeBlank(draft.templateName()) != null ? normalizeBlank(draft.templateName()) : "");
        auditContext.put("releaseId", normalizeBlank(draft.releaseId()) != null ? normalizeBlank(draft.releaseId()) : "");
        auditContext.put("snowGroup", normalizeBlank(draft.snowGroup()) != null ? normalizeBlank(draft.snowGroup()) : "");
        auditContext.put("application", normalizeBlank(draft.application()) != null ? normalizeBlank(draft.application()) : "");
        auditContext.put("agent", normalizeBlank(draft.agent()) != null ? normalizeBlank(draft.agent()) : "");
        auditContext.put("site", normalizeBlank(draft.site()) != null ? normalizeBlank(draft.site()) : "");
        return auditContext;
    }

    private Stage requireStage(Stage stage) {
        if (stage == null) {
            throw new ValidationAppException("Stage is required.");
        }
        return stage;
    }

    private List<CreateRundownFromTemplateTaskDto> normalizeTasks(List<CreateRundownFromTemplateTaskDto> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            throw new ValidationAppException("At least one template task is required to create a rundown.");
        }

        List<CreateRundownFromTemplateTaskDto> normalized = new ArrayList<>(tasks);
        normalized.sort((left, right) -> {
            int leftStep = left.step() != null ? left.step() : Integer.MAX_VALUE;
            int rightStep = right.step() != null ? right.step() : Integer.MAX_VALUE;
            return Integer.compare(leftStep, rightStep);
        });

        for (CreateRundownFromTemplateTaskDto task : normalized) {
            if (task.step() == null || task.step() < 1) {
                throw new ValidationAppException("Template task step must be 1 or higher.");
            }
            requireValue(task.taskName(), "Task name is required.");
            requireValue(task.stepName(), "Task step name is required.");
            if (task.type() == null) {
                throw new ValidationAppException("Task execution type is required.");
            }
            if (task.estDurationMinutes() != null && task.estDurationMinutes() < 0) {
                throw new ValidationAppException("Task duration cannot be negative.");
            }
        }

        return normalized;
    }

    private Integer resolveEstimatedRemainingMinutes(
            Integer estimatedRemainingMinutes,
            List<CreateRundownFromTemplateTaskDto> tasks) {
        if (estimatedRemainingMinutes != null) {
            if (estimatedRemainingMinutes < 0) {
                throw new ValidationAppException("Estimated remaining minutes cannot be negative.");
            }
            return estimatedRemainingMinutes;
        }

        int totalMinutes = tasks.stream()
                .map(CreateRundownFromTemplateTaskDto::estDurationMinutes)
                .filter(minutes -> minutes != null && minutes > 0)
                .mapToInt(Integer::intValue)
                .sum();

        return totalMinutes > 0 ? totalMinutes : null;
    }

    private String validateReleaseIdentifier(String releaseIdentifier, Stage stage) {
        String normalized = requireValue(releaseIdentifier, "Release identifier is required.");
        var matcher = RELEASE_IDENTIFIER_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            throw new ValidationAppException(
                    "Release identifier must match xxx-sit-01 / xxx-uat-01 / xxx-prod-01.");
        }

        String releaseStage = matcher.group("stage");
        if (!stage.name().equalsIgnoreCase(releaseStage)) {
            throw new ValidationAppException(
                    "Release identifier stage segment must match the selected stage '" + stage.name() + "'.");
        }

        return normalized;
    }

    private String resolveRequestOwner(
            String preferredOwner,
            List<CreateRundownFromTemplateTaskDto> tasks,
            UserContext user) {
        String normalizedPreferredOwner = normalizeBlank(preferredOwner);
        if (normalizedPreferredOwner != null) {
            return normalizedPreferredOwner;
        }

        List<String> uniqueOwners = tasks.stream()
                .map(CreateRundownFromTemplateTaskDto::owner)
                .map(this::normalizeBlank)
                .filter(value -> value != null)
                .distinct()
                .toList();

        if (uniqueOwners.size() == 1) {
            return uniqueOwners.get(0);
        }

        String displayName = user != null ? normalizeBlank(user.displayName()) : null;
        if (displayName != null) {
            return displayName;
        }

        return user != null ? user.userId() : null;
    }

    private String deriveProjectId(String projectName) {
        String normalized = projectName.trim().toUpperCase()
                .replaceAll("[^A-Z0-9]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
        if (normalized.isBlank()) {
            throw new ValidationAppException("Project ID could not be derived from project name.");
        }
        return normalized;
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

    private String requireValue(String value, String message) {
        String normalized = normalizeBlank(value);
        if (normalized == null) {
            throw new ValidationAppException(message);
        }
        return normalized;
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

    private Integer coalesceInt(Integer preferred, Integer existing) {
        return preferred != null ? preferred : existing;
    }

    private String normalizeBlank(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
