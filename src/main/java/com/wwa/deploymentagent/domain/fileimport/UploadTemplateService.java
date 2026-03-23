package com.wwa.deploymentagent.domain.fileimport;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Generates a valid starter workbook that callers can download and fill in.
 */
@Service
public class UploadTemplateService {

    private static final String[] HEADERS = {
            "Project ID", "Project Name", "Task ID", "Task Name",
            "Step seq#", "Step", "Execution Type",
            "Script to be executed", "Parameter (input)",
            "Parameter (Expected Output)", "Owner",
            "Planned Start date/time", "Planned End date/time",
            "Activity category", "Common", "Dependencies", "Validation", "Critical",
            "Status", "Start date/time", "End date/time"
    };

    public byte[] generateTemplate() throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(ExcelParserService.SHEET_NAME);

            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
            }

            Row sample = sheet.createRow(1);
            sample.createCell(0).setCellValue("WF-PROJ-001");
            sample.createCell(1).setCellValue("Workflow Project");
            sample.createCell(2).setCellValue("TG-001");
            sample.createCell(3).setCellValue("Deploy Application");
            sample.createCell(4).setCellValue("1");
            sample.createCell(5).setCellValue("deploy-step-1");
            sample.createCell(6).setCellValue("MANUAL");
            sample.createCell(7).setCellValue("deploy.sh");
            sample.createCell(8).setCellValue("--env uat");
            sample.createCell(9).setCellValue("Deployment succeeds");
            sample.createCell(10).setCellValue("alice");
            sample.createCell(11).setCellValue("2026-03-22T09:00:00Z");
            sample.createCell(12).setCellValue("2026-03-22T09:30:00Z");
            sample.createCell(13).setCellValue("Application");
            sample.createCell(14).setCellValue("N");
            sample.createCell(15).setCellValue("DB ready");
            sample.createCell(16).setCellValue("Smoke test passes");
            sample.createCell(17).setCellValue("Y");

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(output);
            return output.toByteArray();
        }
    }
}
