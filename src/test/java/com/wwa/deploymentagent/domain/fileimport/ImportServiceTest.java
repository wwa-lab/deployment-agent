package com.wwa.deploymentagent.domain.fileimport;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlowRepository;
import com.wwa.deploymentagent.domain.releaseflow.RequestRepository;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlowService;
import com.wwa.deploymentagent.domain.task.TaskRepository;
import com.wwa.deploymentagent.errors.ImportValidationException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("ImportService")
class ImportServiceTest {

    @Autowired private ImportService importService;
    @Autowired private ReleaseFlowRepository releaseFlowRepository;
    @Autowired private ReleaseFlowService releaseFlowService;
    @Autowired private RequestRepository requestRepository;
    @Autowired private TaskRepository taskRepository;

    private final UserContext developer = new UserContext("dev-1", "DEVELOPER");

    // ─── Headers ──────────────────────────────────────────────────────────────

    private static final String[] HEADERS = {
            "Project ID", "Project Name", "Task ID", "Task Name",
            "Step seq#", "Step", "Execution Type",
            "Script to be executed", "Parameter (input)",
            "Parameter (Expected Output)", "Owner",
            "Planned Start date/time", "Planned End date/time",
            "Activity category", "Common", "Dependencies", "Validation"
    };

    // ─── Tests ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("new project creates Release Flow with correct release ID format")
    void importFile_newProject_createsReleaseFlow() throws IOException {
        byte[] xlsx = buildXlsx("PAYMENT-HUB", "Payment Hub", "TG-01", "Deploy", 1, "step-1", "MANUAL");

        ImportResult result = importService.importFile(xlsx, "SIT", developer);

        assertThat(result.releaseFlowId()).isNotNull();
        assertThat(result.releaseId()).startsWith("sit-");
        assertThat(result.releaseId()).contains("paymenthub");
        assertThat(result.releaseId()).matches("sit-paymenthub-\\d{4}");
        assertThat(result.stage()).isEqualTo("SIT");
        assertThat(result.taskCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("existing project: new stage attaches Request to same Release Flow")
    void importFile_existingProject_attachesNewRequest() throws IOException {
        // First upload for SIT
        byte[] sitXlsx = buildXlsx("PROJ-A", "Project A", "TG-01", "Task", 1, "step-1", "MANUAL");
        ImportResult sitResult = importService.importFile(sitXlsx, "SIT", developer);

        // Second upload for UAT – same project
        byte[] uatXlsx = buildXlsx("PROJ-A", "Project A", "TG-01", "Task", 1, "uat-step", "MANUAL");
        ImportResult uatResult = importService.importFile(uatXlsx, "UAT", developer);

        assertThat(uatResult.releaseFlowId()).isEqualTo(sitResult.releaseFlowId());

        long requestCount = requestRepository.findByReleaseFlowId(sitResult.releaseFlowId()).size();
        assertThat(requestCount).isEqualTo(2); // SIT + UAT
    }

    @Test
    @DisplayName("same project + same stage upload creates a new release flow instead of overwriting the existing rundown")
    void importFile_sameStageUpload_createsNewReleaseFlow() throws IOException {
        byte[] first = buildXlsx("PROJ-B", "Project B", "TG-01", "Task B", 1, "original-step", "MANUAL");
        ImportResult firstResult = importService.importFile(first, "SIT", developer);

        byte[] second = buildXlsx("PROJ-B", "Project B", "TG-01", "Task B", 1, "updated-step", "MANUAL");
        ImportResult secondResult = importService.importFile(second, "SIT", developer);

        assertThat(secondResult.releaseFlowId()).isNotEqualTo(firstResult.releaseFlowId());
        assertThat(secondResult.releaseId()).isNotEqualTo(firstResult.releaseId());

        var firstRequests = requestRepository.findByReleaseFlowId(firstResult.releaseFlowId());
        var secondRequests = requestRepository.findByReleaseFlowId(secondResult.releaseFlowId());

        assertThat(firstRequests).hasSize(1);
        assertThat(secondRequests).hasSize(1);

        var firstTasks = taskRepository.findByRequestIdOrderByTaskGroupIdAscStepSeqAsc(firstRequests.get(0).getId());
        var secondTasks = taskRepository.findByRequestIdOrderByTaskGroupIdAscStepSeqAsc(secondRequests.get(0).getId());

        assertThat(firstTasks).hasSize(1);
        assertThat(secondTasks).hasSize(1);
        assertThat(firstTasks.get(0).getTaskName()).isEqualTo("original-step");
        assertThat(secondTasks.get(0).getTaskName()).isEqualTo("updated-step");
    }

    @Test
    @DisplayName("multi-row import preserves distinct task input parameters")
    void importFile_multiRowDistinctInputs_preservesEachTaskInput() throws IOException {
        byte[] xlsx = buildMultiRowXlsx(List.of(
                row("PROJ-MULTI", "Project Multi", "TG-01", "Task Multi",
                        "1", "step-1", "AUTO",
                        "first-job", "--env sit",
                        "", "", "", "", "", "", "", ""),
                row("PROJ-MULTI", "Project Multi", "TG-01", "Task Multi",
                        "2", "step-2", "AUTO",
                        "second-job", "--env uat",
                        "", "", "", "", "", "", "", "")
        ));

        ImportResult result = importService.importFile(xlsx, "SIT", developer);

        var requests = requestRepository.findByReleaseFlowId(result.releaseFlowId());
        assertThat(requests).hasSize(1);

        var tasks = taskRepository.findByRequestIdOrderByTaskGroupIdAscStepSeqAsc(requests.get(0).getId());
        assertThat(tasks).hasSize(2);
        assertThat(tasks.get(0).getInputParameters())
                .containsEntry("script", "first-job")
                .containsEntry("parameters", "--env sit");
        assertThat(tasks.get(1).getInputParameters())
                .containsEntry("script", "second-job")
                .containsEntry("parameters", "--env uat");
    }

    @Test
    @DisplayName("later-stage upload attaches to the newest release flow that does not already have that stage")
    void importFile_laterStageUpload_usesNewestEligibleReleaseFlow() throws IOException {
        ImportResult firstSit = importService.importFile(
                buildXlsx("PROJ-D", "Project D", "TG-01", "Task D", 1, "sit-step-1", "MANUAL"),
                "SIT",
                developer);
        ImportResult secondSit = importService.importFile(
                buildXlsx("PROJ-D", "Project D", "TG-01", "Task D", 1, "sit-step-2", "MANUAL"),
                "SIT",
                developer);

        ImportResult uatResult = importService.importFile(
                buildXlsx("PROJ-D", "Project D", "TG-01", "Task D", 1, "uat-step", "MANUAL"),
                "UAT",
                developer);

        assertThat(uatResult.releaseFlowId()).isEqualTo(secondSit.releaseFlowId());
        assertThat(uatResult.releaseFlowId()).isNotEqualTo(firstSit.releaseFlowId());

        assertThat(requestRepository.findByReleaseFlowId(secondSit.releaseFlowId()))
                .extracting(request -> request.getStage())
                .containsExactlyInAnyOrder("SIT", "UAT");
        assertThat(requestRepository.findByReleaseFlowId(firstSit.releaseFlowId()))
                .extracting(request -> request.getStage())
                .containsExactly("SIT");
    }

    @Test
    @DisplayName("explicit release identifier keeps later-stage uploads on the same release flow")
    void importFile_explicitReleaseIdentifier_reusesMatchingReleaseFlow() throws IOException {
        String releaseIdentifier = "Workflow-Release-20260327-01";

        ImportResult sitResult = importService.importFile(
                buildXlsx("PROJ-E", "Project E", "TG-01", "Task E", 1, "sit-step", "MANUAL"),
                "SIT",
                developer,
                releaseIdentifier,
                null,
                null,
                null);
        ImportResult uatResult = importService.importFile(
                buildXlsx("PROJ-E", "Project E", "TG-01", "Task E", 1, "uat-step", "MANUAL"),
                "UAT",
                developer,
                releaseIdentifier,
                null,
                null,
                null);

        assertThat(sitResult.releaseId()).isEqualTo(releaseIdentifier);
        assertThat(uatResult.releaseId()).isEqualTo(releaseIdentifier);
        assertThat(uatResult.releaseFlowId()).isEqualTo(sitResult.releaseFlowId());
        assertThat(requestRepository.findByReleaseFlowId(sitResult.releaseFlowId()))
                .extracting(request -> request.getStage())
                .containsExactlyInAnyOrder("SIT", "UAT");
    }

    @Test
    @DisplayName("stage-prefixed explicit identifiers are stitched into the same release family")
    void importFile_stagePrefixedExplicitIdentifiers_shareReleaseFamily() throws IOException {
        ImportResult sitResult = importService.importFile(
                buildXlsx("PROJ-E1", "Project E1", "TG-01", "Task E1", 1, "sit-step", "MANUAL"),
                "SIT",
                developer,
                "sit-01",
                null,
                null,
                null);
        ImportResult uatResult = importService.importFile(
                buildXlsx("PROJ-E1", "Project E1", "TG-01", "Task E1", 1, "uat-step", "MANUAL"),
                "UAT",
                developer,
                "uat-01",
                null,
                null,
                null);

        assertThat(uatResult.releaseFlowId()).isEqualTo(sitResult.releaseFlowId());
        assertThat(requestRepository.findByReleaseFlowId(sitResult.releaseFlowId()))
                .extracting(request -> request.getStage())
                .containsExactlyInAnyOrder("SIT", "UAT");
    }

    @Test
    @DisplayName("explicit release identifier allows repeated same-stage uploads as incremented attempts")
    void importFile_explicitReleaseIdentifier_incrementsAttemptForDuplicateStage() throws IOException {
        String releaseIdentifier = "Workflow-Release-20260327-02";

        importService.importFile(
                buildXlsx("PROJ-F", "Project F", "TG-01", "Task F", 1, "sit-step", "MANUAL"),
                "SIT",
                developer,
                releaseIdentifier,
                null,
                null,
                null);

        ImportResult retryResult = importService.importFile(
                buildXlsx("PROJ-F", "Project F", "TG-01", "Task F", 1, "sit-step-2", "MANUAL"),
                "SIT",
                developer,
                releaseIdentifier,
                null,
                null,
                null);

        var requests = requestRepository.findByReleaseFlowId(retryResult.releaseFlowId()).stream()
                .filter(request -> "SIT".equals(request.getStage()))
                .sorted(java.util.Comparator.comparing(request -> request.getAttemptNumber()))
                .toList();

        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).getAttemptNumber()).isEqualTo(1);
        assertThat(requests.get(1).getAttemptNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("infix stage identifiers stitch repeated SIT uploads with UAT and PROD in one release flow")
    void importFile_infixStageIdentifiers_stitchRetriesAndProgression() throws IOException {
        ImportResult sitFirst = importService.importFile(
                buildXlsx("PROJ-I", "Project I", "TG-01", "Task I", 1, "sit-step-1", "MANUAL"),
                "SIT",
                developer,
                "leo-sit-01",
                null,
                null,
                null);
        ImportResult sitSecond = importService.importFile(
                buildXlsx("PROJ-I", "Project I", "TG-01", "Task I", 1, "sit-step-2", "MANUAL"),
                "SIT",
                developer,
                "leo-sit-02",
                null,
                null,
                null);
        ImportResult uatResult = importService.importFile(
                buildXlsx("PROJ-I", "Project I", "TG-01", "Task I", 1, "uat-step", "MANUAL"),
                "UAT",
                developer,
                "leo-uat-01",
                null,
                null,
                null);
        ImportResult prodResult = importService.importFile(
                buildXlsx("PROJ-I", "Project I", "TG-01", "Task I", 1, "prod-step", "MANUAL"),
                "PROD",
                developer,
                "leo-prod-01",
                null,
                null,
                null);

        assertThat(sitSecond.releaseFlowId()).isEqualTo(sitFirst.releaseFlowId());
        assertThat(uatResult.releaseFlowId()).isEqualTo(sitFirst.releaseFlowId());
        assertThat(prodResult.releaseFlowId()).isEqualTo(sitFirst.releaseFlowId());

        var requests = requestRepository.findByReleaseFlowId(sitFirst.releaseFlowId());
        assertThat(requests.stream().filter(request -> "SIT".equals(request.getStage()))).hasSize(2);
        assertThat(requests.stream().filter(request -> "UAT".equals(request.getStage()))).hasSize(1);
        assertThat(requests.stream().filter(request -> "PROD".equals(request.getStage()))).hasSize(1);
    }

    @Test
    @DisplayName("system-generated release id can be reused as an explicit identifier for later stages")
    void importFile_generatedReleaseId_canBeReusedExplicitly() throws IOException {
        ImportResult sitResult = importService.importFile(
                buildXlsx("PROJ-G", "Project G", "TG-01", "Task G", 1, "sit-step", "MANUAL"),
                "SIT",
                developer);

        ImportResult uatResult = importService.importFile(
                buildXlsx("PROJ-G", "Project G", "TG-01", "Task G", 1, "uat-step", "MANUAL"),
                "UAT",
                developer,
                sitResult.releaseId(),
                null,
                null,
                null);

        assertThat(uatResult.releaseFlowId()).isEqualTo(sitResult.releaseFlowId());
        assertThat(uatResult.releaseId()).isEqualTo(sitResult.releaseId());
    }

    @Test
    @DisplayName("explicit release identifier still matches legacy flows stored with the older normalized id format")
    void importFile_explicitReleaseIdentifier_matchesLegacyNormalizedReleaseFlow() throws IOException {
        String legacyReleaseId = "sit-projh-0007";
        var legacyFlow = releaseFlowService.create(
                "PROJ-H",
                "Project H",
                legacyReleaseId,
                legacyReleaseId.toLowerCase(),
                "SIT");

        ImportResult uatResult = importService.importFile(
                buildXlsx("PROJ-H", "Project H", "TG-01", "Task H", 1, "uat-step", "MANUAL"),
                "UAT",
                developer,
                legacyReleaseId,
                null,
                null,
                null);

        assertThat(uatResult.releaseFlowId()).isEqualTo(legacyFlow.getId());
        assertThat(uatResult.releaseId()).isEqualTo(legacyReleaseId);
    }

    @Test
    @DisplayName("validation error throws ImportValidationException (no partial DB writes)")
    void importFile_validationError_throwsException() throws IOException {
        // Missing required Project ID
        byte[] badXlsx = buildXlsxWithMissingProjectId();

        assertThatThrownBy(() -> importService.importFile(badXlsx, "SIT", developer))
                .isInstanceOf(ImportValidationException.class)
                .satisfies(ex -> {
                    ImportValidationException ive = (ImportValidationException) ex;
                    assertThat(ive.getErrors()).isNotEmpty();
                });
    }

    @Test
    @DisplayName("single task owner becomes the default rundown owner on import")
    void importFile_singleTaskOwner_setsRequestOwner() throws IOException {
        byte[] xlsx = buildXlsx("PROJ-C", "Project C", "TG-01", "Task C", 1, "step-1", "MANUAL", "alice");

        ImportResult result = importService.importFile(xlsx, "SIT", developer);

        var requests = requestRepository.findByReleaseFlowId(result.releaseFlowId());
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).getOwner()).isEqualTo("alice");
    }

    // ─── XLSX builder helpers ─────────────────────────────────────────────────

    private byte[] buildXlsx(String projectId, String projectName,
                              String taskGroupId, String taskGroupName,
                              int stepSeq, String step, String execType) throws IOException {
        return buildXlsx(projectId, projectName, taskGroupId, taskGroupName, stepSeq, step, execType, null);
    }

    private byte[] buildXlsx(String projectId, String projectName,
                              String taskGroupId, String taskGroupName,
                              int stepSeq, String step, String execType, String owner) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(ExcelParserService.SHEET_NAME);

            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) header.createCell(i).setCellValue(HEADERS[i]);

            Row data = sheet.createRow(1);
            data.createCell(0).setCellValue(projectId);
            data.createCell(1).setCellValue(projectName);
            data.createCell(2).setCellValue(taskGroupId);
            data.createCell(3).setCellValue(taskGroupName);
            data.createCell(4).setCellValue(String.valueOf(stepSeq));
            data.createCell(5).setCellValue(step);
            data.createCell(6).setCellValue(execType);
            if (owner != null) {
                data.createCell(10).setCellValue(owner);
            }
            // remaining columns left blank (optional)

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private byte[] buildMultiRowXlsx(List<String[]> rows) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(ExcelParserService.SHEET_NAME);

            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
            }

            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Row data = sheet.createRow(rowIndex + 1);
                String[] values = rows.get(rowIndex);
                for (int colIndex = 0; colIndex < values.length; colIndex++) {
                    if (values[colIndex] != null && !values[colIndex].isEmpty()) {
                        data.createCell(colIndex).setCellValue(values[colIndex]);
                    }
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private String[] row(String projectId, String projectName,
                         String taskGroupId, String taskGroupName,
                         String stepSeq, String step, String execType,
                         String script, String parameters,
                         String expectedOutput, String owner,
                         String plannedStart, String plannedEnd,
                         String activityCategory, String common,
                         String dependencies, String validation) {
        return new String[] {
                projectId, projectName, taskGroupId, taskGroupName,
                stepSeq, step, execType,
                script, parameters,
                expectedOutput, owner,
                plannedStart, plannedEnd,
                activityCategory, common, dependencies, validation
        };
    }

    private byte[] buildXlsxWithMissingProjectId() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(ExcelParserService.SHEET_NAME);

            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) header.createCell(i).setCellValue(HEADERS[i]);

            Row data = sheet.createRow(1);
            // Project ID intentionally left blank (cell 0 not set)
            data.createCell(1).setCellValue("Some Project");
            data.createCell(2).setCellValue("TG-01");
            data.createCell(3).setCellValue("Task");
            data.createCell(4).setCellValue("1");
            data.createCell(5).setCellValue("step");
            data.createCell(6).setCellValue("MANUAL");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }
}
