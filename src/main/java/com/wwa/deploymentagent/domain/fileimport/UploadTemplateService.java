package com.wwa.deploymentagent.domain.fileimport;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Generates a valid starter workbook that callers can download and fill in.
 *
 * <p>The concrete schema (sheet name, column headers, sample row, downloadable
 * file name) is resolved from {@link TemplateSchemaRegistry} so that the
 * template for a given agent is defined in exactly one place and stays in
 * sync with any future per-agent customization.
 *
 * <p>MVP Foundation Seam — the no-arg {@link #generateTemplate()} overload is
 * preserved for existing callers (including test fixtures) and returns the
 * default schema bytes.
 */
@Service
@RequiredArgsConstructor
public class UploadTemplateService {

    private final TemplateSchemaRegistry schemaRegistry;

    /**
     * Generates the default (shared) template. Kept for backwards compatibility
     * with existing tests and the legacy {@code /api/platform/upload/template}
     * route; per-agent callers should prefer {@link #generateTemplate(String)}.
     */
    public byte[] generateTemplate() throws IOException {
        return generateFromSchema(TemplateSchemaRegistry.DEFAULT_SCHEMA);
    }

    /**
     * Generates the template bytes for a specific agent. Day-1 this returns
     * the same bytes as {@link #generateTemplate()} for every agent because
     * all agents share the default schema, but callers should route through
     * this overload so future per-agent divergence is picked up automatically.
     */
    public byte[] generateTemplate(String agentId) throws IOException {
        return generateFromSchema(schemaRegistry.resolve(agentId));
    }

    /**
     * Exposes the resolved schema for callers that need both the bytes and
     * metadata such as the downloadable file name (used by the HTTP layer to
     * populate the {@code Content-Disposition} header).
     */
    public TemplateSchema resolveSchema(String agentId) {
        return schemaRegistry.resolve(agentId);
    }

    private byte[] generateFromSchema(TemplateSchema schema) throws IOException {
        List<String> headers = schema.headers();
        List<List<String>> sampleRows = schema.sampleRows();

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(schema.sheetName());

            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                header.createCell(i).setCellValue(headers.get(i));
            }

            for (int rowIndex = 0; rowIndex < sampleRows.size(); rowIndex++) {
                List<String> sampleRow = sampleRows.get(rowIndex);
                if (sampleRow.isEmpty()) {
                    continue;
                }
                Row sample = sheet.createRow(rowIndex + 1);
                for (int i = 0; i < sampleRow.size(); i++) {
                    sample.createCell(i).setCellValue(sampleRow.get(i));
                }
            }

            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(output);
            return output.toByteArray();
        }
    }
}
