package com.wwa.deploymentagent.domain.fileimport;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.enums.AuditActionType;
import com.wwa.deploymentagent.contracts.enums.RequestStatus;
import com.wwa.deploymentagent.contracts.enums.Stage;
import com.wwa.deploymentagent.contracts.enums.TaskStatus;
import com.wwa.deploymentagent.domain.audit.AuditLoggerService;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlow;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlowRepository;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlowService;
import com.wwa.deploymentagent.domain.releaseflow.Request;
import com.wwa.deploymentagent.domain.releaseflow.RequestRepository;
import com.wwa.deploymentagent.domain.task.Task;
import com.wwa.deploymentagent.domain.task.TaskRepository;
import com.wwa.deploymentagent.errors.ImportValidationException;
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
 *   <li>Finds or creates a Release Flow per project</li>
 *   <li>Finds or creates a Request for the upload stage</li>
 *   <li>Upserts tasks (preserving execution state on re-upload)</li>
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
    public ImportResult importFile(byte[] fileBytes, Stage stage, UserContext user) throws IOException {
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

            ReleaseFlow rf = findOrCreateReleaseFlow(projectId, projectName, stage);

            Request request = findOrCreateRequest(rf, stage);

            for (ParsedTaskRow row : rows) {
                upsertTask(request, row);
                totalTaskCount++;
            }

            lastReleaseFlowId = rf.getId();
            lastReleaseId     = rf.getReleaseId();
        }

        auditLogger.log(user, AuditActionType.upload, lastReleaseFlowId, null, null,
                Map.of("stage", stage.name(), "taskCount", totalTaskCount));

        return new ImportResult(lastReleaseFlowId, lastReleaseId, stage, totalTaskCount);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private ReleaseFlow findOrCreateReleaseFlow(String projectId, String projectName, Stage stage) {
        Optional<ReleaseFlow> existing = releaseFlowRepository.findFirstByProjectId(projectId);
        if (existing.isPresent()) {
            return existing.get();
        }
        String normalized    = normalizeId(projectId);
        long count           = releaseFlowRepository.countByProjectId(projectId);
        String genReleaseId  = stage.name().toLowerCase() + "-" + normalized
                + "-" + String.format("%04d", count + 1);
        return releaseFlowService.create(projectId, projectName, genReleaseId, genReleaseId, stage);
    }

    private Request findOrCreateRequest(ReleaseFlow rf, Stage stage) {
        return requestRepository.findByReleaseFlowIdAndStage(rf.getId(), stage)
                .orElseGet(() -> {
                    Request req = new Request();
                    req.setReleaseFlow(rf);
                    req.setStage(stage);
                    req.setRequestStatus(RequestStatus.Pending);
                    return requestRepository.save(req);
                });
    }

    private void upsertTask(Request request, ParsedTaskRow row) {
        Task task = taskRepository
                .findByRequestIdAndTaskGroupIdAndStepSeq(request.getId(), row.taskGroupId(), row.stepSeq())
                .orElseGet(Task::new);

        boolean isNew = task.getId() == null;
        if (isNew) {
            task.setRequest(request);
            task.setTaskStatus(TaskStatus.Pending);
        }
        // Update all template-derived fields; preserve execution state on re-upload
        task.setTaskGroupId(row.taskGroupId());
        task.setTaskGroupName(row.taskGroupName());
        task.setStepSeq(row.stepSeq());
        task.setTaskName(row.taskName());
        task.setExecutionType(row.executionType());
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
}
