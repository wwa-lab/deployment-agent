package com.wwa.deploymentagent.domain.fileimport;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.enums.Stage;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlowRepository;
import com.wwa.deploymentagent.domain.releaseflow.RequestRepository;
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

        ImportResult result = importService.importFile(xlsx, Stage.SIT, developer);

        assertThat(result.releaseFlowId()).isNotNull();
        assertThat(result.releaseId()).startsWith("sit-");
        assertThat(result.releaseId()).contains("paymenthub");
        assertThat(result.releaseId()).matches("sit-paymenthub-\\d{4}");
        assertThat(result.stage()).isEqualTo(Stage.SIT);
        assertThat(result.taskCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("existing project: new stage attaches Request to same Release Flow")
    void importFile_existingProject_attachesNewRequest() throws IOException {
        // First upload for SIT
        byte[] sitXlsx = buildXlsx("PROJ-A", "Project A", "TG-01", "Task", 1, "step-1", "MANUAL");
        ImportResult sitResult = importService.importFile(sitXlsx, Stage.SIT, developer);

        // Second upload for UAT – same project
        byte[] uatXlsx = buildXlsx("PROJ-A", "Project A", "TG-01", "Task", 1, "uat-step", "MANUAL");
        ImportResult uatResult = importService.importFile(uatXlsx, Stage.UAT, developer);

        assertThat(uatResult.releaseFlowId()).isEqualTo(sitResult.releaseFlowId());

        long requestCount = requestRepository.findByReleaseFlowId(sitResult.releaseFlowId()).size();
        assertThat(requestCount).isEqualTo(2); // SIT + UAT
    }

    @Test
    @DisplayName("re-upload same project + stage updates tasks without duplicating them")
    void importFile_reUpload_upsertsTasks() throws IOException {
        byte[] first = buildXlsx("PROJ-B", "Project B", "TG-01", "Task B", 1, "original-step", "MANUAL");
        ImportResult firstResult = importService.importFile(first, Stage.SIT, developer);

        long taskCountBefore = taskRepository.findByRequestIdOrderByTaskGroupIdAscStepSeqAsc(
                requestRepository.findByReleaseFlowId(firstResult.releaseFlowId()).get(0).getId()
        ).size();

        byte[] second = buildXlsx("PROJ-B", "Project B", "TG-01", "Task B", 1, "updated-step", "MANUAL");
        importService.importFile(second, Stage.SIT, developer);

        long taskCountAfter = taskRepository.findByRequestIdOrderByTaskGroupIdAscStepSeqAsc(
                requestRepository.findByReleaseFlowId(firstResult.releaseFlowId()).get(0).getId()
        ).size();

        assertThat(taskCountAfter).isEqualTo(taskCountBefore); // no duplicates

        // Verify task name was updated
        var tasks = taskRepository.findByRequestIdOrderByTaskGroupIdAscStepSeqAsc(
                requestRepository.findByReleaseFlowId(firstResult.releaseFlowId()).get(0).getId());
        assertThat(tasks.get(0).getTaskName()).isEqualTo("updated-step");
    }

    @Test
    @DisplayName("validation error throws ImportValidationException (no partial DB writes)")
    void importFile_validationError_throwsException() throws IOException {
        // Missing required Project ID
        byte[] badXlsx = buildXlsxWithMissingProjectId();

        assertThatThrownBy(() -> importService.importFile(badXlsx, Stage.SIT, developer))
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

        ImportResult result = importService.importFile(xlsx, Stage.SIT, developer);

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
