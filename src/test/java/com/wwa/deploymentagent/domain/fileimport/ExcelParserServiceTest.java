package com.wwa.deploymentagent.domain.fileimport;

import com.wwa.deploymentagent.contracts.enums.ExecutionType;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExcelParserService")
class ExcelParserServiceTest {

    private ExcelParserService parser;

    @BeforeEach
    void setUp() {
        parser = new ExcelParserService();
    }

    // ─── Column index constants ───────────────────────────────────────────────

    private static final String[] HEADERS = {
            "Project ID", "Project Name", "Task ID", "Task Name",
            "Step seq#", "Step", "Execution Type",
            "Script to be executed", "Parameter (input)",
            "Parameter (Expected Output)", "Owner",
            "Planned Start date/time", "Planned End date/time",
            "Activity category", "Common", "Dependencies", "Validation",
            "Status", "Start date/time", "End date/time"
    };

    // ─── Valid parse ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("valid file parses all rows with correct field values")
    void parse_validFile_returnsRows() throws IOException {
        byte[] xlsx = buildXlsx(List.of(
                row("PROJ-1", "ProjectOne", "TG-001", "Deploy App",
                    "1", "deploy-step", "AUTO",
                    "deploy.sh", "--env prod",
                    "OK", "alice",
                    "", "",
                    "CAT-A", "yes", "none", "validated",
                    "Done", "", "")
        ));

        ParseResult result = parser.parse(xlsx);

        assertThat(result.hasErrors()).isFalse();
        assertThat(result.rows()).hasSize(1);

        ParsedTaskRow r = result.rows().get(0);
        assertThat(r.projectId()).isEqualTo("PROJ-1");
        assertThat(r.projectName()).isEqualTo("ProjectOne");
        assertThat(r.taskGroupId()).isEqualTo("TG-001");
        assertThat(r.taskGroupName()).isEqualTo("Deploy App");
        assertThat(r.stepSeq()).isEqualTo(1);
        assertThat(r.taskName()).isEqualTo("deploy-step");
        assertThat(r.executionType()).isEqualTo(ExecutionType.AUTO);
        assertThat(r.inputParameters()).containsEntry("script", "deploy.sh");
        assertThat(r.inputParameters()).containsEntry("parameters", "--env prod");
        assertThat(r.expectedOutput()).isEqualTo("OK");
        assertThat(r.owner()).isEqualTo("alice");
        assertThat(r.importMetadata()).containsEntry("activity_category", "CAT-A");
        assertThat(r.importMetadata()).containsEntry("common", "yes");
    }

    @Test
    @DisplayName("execution type is case-insensitive (auto / MANUAL)")
    void parse_executionTypeCaseInsensitive() throws IOException {
        byte[] xlsx = buildXlsx(List.of(
                row("P", "N", "TG-1", "T", "1", "s", "manual",
                    "", "", "", "", "", "", "", "", "", "", "", "", "")
        ));

        ParseResult result = parser.parse(xlsx);
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.rows().get(0).executionType()).isEqualTo(ExecutionType.MANUAL);
    }

    @Test
    @DisplayName("ignored columns (Status, Start date/time, End date/time) are not stored")
    void parse_ignoredColumnsNotStored() throws IOException {
        byte[] xlsx = buildXlsx(List.of(
                row("P", "N", "TG-1", "T", "1", "s", "MANUAL",
                    "", "", "", "", "", "", "", "", "", "", "Running", "2025-01-01", "2025-01-02")
        ));

        ParseResult result = parser.parse(xlsx);
        assertThat(result.hasErrors()).isFalse();
        ParsedTaskRow r = result.rows().get(0);
        // importMetadata should not contain Status, Start date/time, End date/time
        if (r.importMetadata() != null) {
            assertThat(r.importMetadata()).doesNotContainKey("Status");
            assertThat(r.importMetadata()).doesNotContainKey("Start date/time");
            assertThat(r.importMetadata()).doesNotContainKey("End date/time");
        }
    }

    // ─── Validation errors ────────────────────────────────────────────────────

    @Test
    @DisplayName("required field missing → error with row and column")
    void parse_requiredFieldMissing_recordsError() throws IOException {
        byte[] xlsx = buildXlsx(List.of(
                row("", "ProjectOne", "TG-001", "Deploy", "1", "step", "AUTO",
                    "deploy.sh", "", "", "", "", "", "", "", "", "", "", "", "")
        ));

        ParseResult result = parser.parse(xlsx);
        assertThat(result.hasErrors()).isTrue();
        ImportError err = result.errors().get(0);
        assertThat(err.row()).isEqualTo(2); // 1-based, data starts at row 2
        assertThat(err.column()).isEqualTo("Project ID");
    }

    @Test
    @DisplayName("invalid execution type → error on that row")
    void parse_invalidExecutionType_recordsError() throws IOException {
        byte[] xlsx = buildXlsx(List.of(
                row("P", "N", "TG-1", "T", "1", "s", "HYBRID",
                    "", "", "", "", "", "", "", "", "", "", "", "", "")
        ));

        ParseResult result = parser.parse(xlsx);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors()).anyMatch(e -> e.column().equals("Execution Type"));
    }

    @Test
    @DisplayName("AUTO task without script → error")
    void parse_autoWithoutScript_recordsError() throws IOException {
        byte[] xlsx = buildXlsx(List.of(
                row("P", "N", "TG-1", "T", "1", "s", "AUTO",
                    "", "", "", "", "", "", "", "", "", "", "", "", "")
        ));

        ParseResult result = parser.parse(xlsx);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors()).anyMatch(e -> e.column().equals("Script to be executed"));
    }

    @Test
    @DisplayName("duplicate step seq# within same Task ID → error")
    void parse_duplicateStepSeq_recordsError() throws IOException {
        byte[] xlsx = buildXlsx(List.of(
                row("P", "N", "TG-1", "T", "1", "first-step", "MANUAL",
                    "", "", "", "", "", "", "", "", "", "", "", "", ""),
                row("P", "N", "TG-1", "T", "1", "second-step", "MANUAL",
                    "", "", "", "", "", "", "", "", "", "", "", "", "")
        ));

        ParseResult result = parser.parse(xlsx);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors()).anyMatch(e ->
                e.column().equals("Step seq#") && e.message().contains("Duplicate"));
    }

    @Test
    @DisplayName("same step seq# in different Task IDs is allowed")
    void parse_sameSeqDifferentTaskId_noError() throws IOException {
        byte[] xlsx = buildXlsx(List.of(
                row("P", "N", "TG-1", "T1", "1", "step-a", "MANUAL",
                    "", "", "", "", "", "", "", "", "", "", "", "", ""),
                row("P", "N", "TG-2", "T2", "1", "step-b", "MANUAL",
                    "", "", "", "", "", "", "", "", "", "", "", "", "")
        ));

        ParseResult result = parser.parse(xlsx);
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.rows()).hasSize(2);
    }

    @Test
    @DisplayName("missing sheet → error with meaningful message")
    void parse_wrongSheetName_recordsError() throws IOException {
        byte[] xlsx = buildXlsxWithSheet("wrong_sheet_name", List.of(
                row("P", "N", "TG-1", "T", "1", "s", "MANUAL",
                    "", "", "", "", "", "", "", "", "", "", "", "", "")
        ));

        ParseResult result = parser.parse(xlsx);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors().get(0).column()).isEqualTo("Sheet");
    }

    // ─── XLSX builder helpers ─────────────────────────────────────────────────

    private byte[] buildXlsx(List<String[]> dataRows) throws IOException {
        return buildXlsxWithSheet(ExcelParserService.SHEET_NAME, dataRows);
    }

    private byte[] buildXlsxWithSheet(String sheetName, List<String[]> dataRows) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(sheetName);

            // Header row
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
            }

            // Data rows
            for (int r = 0; r < dataRows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                String[] values = dataRows.get(r);
                for (int c = 0; c < values.length; c++) {
                    row.createCell(c).setCellValue(values[c]);
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    /** Convenience: builds a full row aligned to HEADERS order. */
    private String[] row(String projectId, String projectName,
                         String taskId, String taskName,
                         String stepSeq, String step, String execType,
                         String script, String paramInput,
                         String paramOutput, String owner,
                         String plannedStart, String plannedEnd,
                         String activityCat, String common,
                         String dependencies, String validation,
                         String status, String startDate, String endDate) {
        return new String[]{
                projectId, projectName, taskId, taskName, stepSeq, step, execType,
                script, paramInput, paramOutput, owner,
                plannedStart, plannedEnd,
                activityCat, common, dependencies, validation,
                status, startDate, endDate
        };
    }
}
