package com.wwa.deploymentagent.workflow;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.domain.fileimport.ImportResult;
import com.wwa.deploymentagent.domain.fileimport.ImportService;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlow;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlowService;
import com.wwa.deploymentagent.domain.releaseflow.RequestRepository;
import com.wwa.deploymentagent.domain.task.TaskRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
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

/**
 * T13.2 - Full import workflow tests using real in-memory XLSX bytes.
 *
 * Verifies that the full import pipeline (parse → upsert → persist) works
 * end-to-end, including upsert idempotency and multi-stage attachment.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("ExcelImportWorkflow")
class ExcelImportWorkflowTest {

    @Autowired private ImportService importService;
    @Autowired private ReleaseFlowService releaseFlowService;
    @Autowired private RequestRepository requestRepository;
    @Autowired private TaskRepository taskRepository;

    private final UserContext developer = new UserContext("dev-user", "DEVELOPER");

    private static final String[] HEADERS = {
            "Project ID", "Project Name", "Task ID", "Task Name",
            "Step seq#", "Step", "Execution Type",
            "Script to be executed", "Parameter (input)",
            "Parameter (Expected Output)", "Owner",
            "Planned Start date/time", "Planned End date/time",
            "Activity category", "Common", "Dependencies", "Validation",
            "Status", "Start date/time", "End date/time"
    };

    // ─── Tests ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("importFile creates Release Flow with tasks and returns non-null releaseFlowId")
    void importFile_createsReleaseFlowWithTasks() throws IOException {
        byte[] xlsx = buildValidXlsx("WF-PROJ-100", "Workflow Project 100", 3);

        ImportResult result = importService.importFile(xlsx, "SIT", developer);

        assertThat(result.releaseFlowId()).isNotNull();
        assertThat(result.stage()).isEqualTo("SIT");
        assertThat(result.taskCount()).isEqualTo(3);

        // Verify tasks are persisted in the hierarchy
        ReleaseFlow rf = releaseFlowService.getByIdWithFullHierarchy(result.releaseFlowId());
        assertThat(rf.getRequests()).hasSize(1);
        assertThat(rf.getRequests().get(0).getTasks()).hasSize(3);
    }

    @Test
    @DisplayName("re-uploading the same project+stage upserts tasks without duplication")
    void importFile_reUpload_upsertsTasks() throws IOException {
        byte[] firstUpload = buildValidXlsx("WF-PROJ-200", "Workflow Project 200", 2);
        ImportResult firstResult = importService.importFile(firstUpload, "SIT", developer);

        String requestId = requestRepository
                .findByReleaseFlowId(firstResult.releaseFlowId())
                .get(0).getId();
        long taskCountBefore = taskRepository
                .findByRequestIdOrderByTaskGroupIdAscStepSeqAsc(requestId).size();

        // Second upload: same project, same stage → should upsert, not duplicate
        byte[] secondUpload = buildValidXlsx("WF-PROJ-200", "Workflow Project 200", 2);
        importService.importFile(secondUpload, "SIT", developer);

        long taskCountAfter = taskRepository
                .findByRequestIdOrderByTaskGroupIdAscStepSeqAsc(requestId).size();

        assertThat(taskCountAfter).isEqualTo(taskCountBefore);
    }

    @Test
    @DisplayName("importing same project as a different stage without an explicit identifier creates a new Release Flow")
    void importFile_secondStage_withoutIdentifierCreatesNewReleaseFlow() throws IOException {
        byte[] sitXlsx = buildValidXlsx("WF-PROJ-300", "Workflow Project 300", 2);
        ImportResult sitResult = importService.importFile(sitXlsx, "SIT", developer);

        byte[] uatXlsx = buildValidXlsx("WF-PROJ-300", "Workflow Project 300", 2);
        ImportResult uatResult = importService.importFile(uatXlsx, "UAT", developer);

        assertThat(uatResult.releaseFlowId()).isNotEqualTo(sitResult.releaseFlowId());

        List<?> sitRequests = requestRepository.findByReleaseFlowId(sitResult.releaseFlowId());
        List<?> uatRequests = requestRepository.findByReleaseFlowId(uatResult.releaseFlowId());
        assertThat(sitRequests).hasSize(1);
        assertThat(uatRequests).hasSize(1);
    }

    // ─── XLSX builder helper ──────────────────────────────────────────────────

    /**
     * Build a valid in-memory XLSX with the given project details and {@code numTasks} data rows.
     * All tasks are MANUAL to avoid AUTO execution requirements.
     * Each task gets a unique Step seq# (1..numTasks) under task group "TG-001".
     */
    private byte[] buildValidXlsx(String projectId, String projectName, int numTasks) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("AMH_HCC_task");

            // Header row
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
            }

            // Data rows
            for (int taskNum = 1; taskNum <= numTasks; taskNum++) {
                Row data = sheet.createRow(taskNum);
                data.createCell(0).setCellValue(projectId);                          // Project ID
                data.createCell(1).setCellValue(projectName);                        // Project Name
                data.createCell(2).setCellValue("TG-001");                           // Task ID
                data.createCell(3).setCellValue("Deploy Application");               // Task Name
                data.createCell(4).setCellValue(String.valueOf(taskNum));            // Step seq#
                data.createCell(5).setCellValue("deploy-step-" + taskNum);           // Step
                data.createCell(6).setCellValue("MANUAL");                           // Execution Type
                // Columns 7-19 intentionally left blank (optional)
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }
}
