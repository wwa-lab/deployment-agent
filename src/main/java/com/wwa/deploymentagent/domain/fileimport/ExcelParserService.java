package com.wwa.deploymentagent.domain.fileimport;

import com.wwa.deploymentagent.contracts.enums.ExecutionType;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

/**
 * ExcelParserService – parses the {@code AMH_HCC_task} sheet from an XLSX file.
 *
 * <p>Returns a {@link ParseResult} with validated rows or row-level errors.
 * Does not interact with the database – pure parsing and validation only.
 */
@Service
public class ExcelParserService {

    /**
     * MVP sheet name. Derived from the default template schema so that the
     * generator and parser cannot drift. When a future agent registers its
     * own schema with a different sheet name in {@link TemplateSchemaRegistry},
     * the parser will need to accept an {@code agentId} parameter and resolve
     * its sheet name from the registry as well — left for that change.
     */
    static final String SHEET_NAME = TemplateSchemaRegistry.DEFAULT_SCHEMA.sheetName();

    public ParseResult parse(byte[] fileBytes) throws IOException {
        List<ParsedTaskRow> rows = new ArrayList<>();
        List<ImportError> errors = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(fileBytes))) {
            Sheet sheet = workbook.getSheet(SHEET_NAME);
            if (sheet == null) {
                errors.add(new ImportError(0, "Sheet", "Sheet '" + SHEET_NAME + "' not found"));
                return new ParseResult(rows, errors);
            }

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                errors.add(new ImportError(0, "Sheet", "Sheet is empty"));
                return new ParseResult(rows, errors);
            }

            Map<String, Integer> headerIndex = buildHeaderIndex(headerRow);

            // Track (taskGroupId → Set<stepSeq>) for uniqueness check
            Map<String, Set<Integer>> seqByGroup = new HashMap<>();

            int lastRowNum = sheet.getLastRowNum();
            for (int rowIdx = 1; rowIdx <= lastRowNum; rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null || isBlankRow(row, headerIndex)) continue;

                int excelRow = rowIdx + 1; // 1-based for user-visible messages
                parseRow(row, excelRow, headerIndex, seqByGroup, rows, errors);
            }
        }

        return new ParseResult(rows, errors);
    }

    // ─── Row parsing ──────────────────────────────────────────────────────────

    private void parseRow(Row row, int excelRow, Map<String, Integer> headers,
                          Map<String, Set<Integer>> seqByGroup,
                          List<ParsedTaskRow> rows, List<ImportError> errors) {

        String projectId    = getRequired(row, headers, "Project ID",   excelRow, errors);
        String projectName  = getRequired(row, headers, "Project Name", excelRow, errors);
        String taskGroupId  = getRequired(row, headers, "Task ID",      excelRow, errors);
        String taskGroupName= getRequired(row, headers, "Task Name",    excelRow, errors);
        String taskName     = getRequired(row, headers, "Step",         excelRow, errors);
        String execTypeStr  = getRequired(row, headers, "Execution Type", excelRow, errors);
        boolean critical    = parseCritical(row, headers, excelRow, errors);

        Integer stepSeq = parseStepSeq(row, headers, excelRow, errors);

        ExecutionType executionType = parseExecutionType(execTypeStr, excelRow, errors);

        String script = getOptionalString(row, headers, "Script to be executed");
        if (executionType == ExecutionType.AUTO && (script == null || script.isBlank())) {
            errors.add(new ImportError(excelRow, "Script to be executed",
                    "Required for AUTO execution type"));
        }

        // Uniqueness of stepSeq within taskGroupId
        if (taskGroupId != null && stepSeq != null) {
            if (!seqByGroup.computeIfAbsent(taskGroupId, k -> new HashSet<>()).add(stepSeq)) {
                errors.add(new ImportError(excelRow, "Step seq#",
                        "Duplicate step seq# " + stepSeq + " within Task ID '" + taskGroupId + "'"));
                stepSeq = null; // treat as invalid so we don't emit the row
            }
        }

        // Only emit a row when all required fields are valid
        if (projectId == null || projectName == null || taskGroupId == null
                || taskGroupName == null || taskName == null
                || executionType == null || stepSeq == null) {
            return;
        }

        String paramInput    = getOptionalString(row, headers, "Parameter (input)");
        String expectedOutput= getOptionalString(row, headers, "Parameter (Expected Output)");
        String owner         = getOptionalString(row, headers, "Owner");
        Instant plannedStart = getOptionalDate(row, headers, "Planned Start date/time");
        Instant plannedEnd   = getOptionalDate(row, headers, "Planned End date/time");

        Map<String, Object> inputParams = new LinkedHashMap<>();
        if (script != null && !script.isBlank()) inputParams.put("script", script);
        if (paramInput != null && !paramInput.isBlank()) inputParams.put("parameters", paramInput);

        Map<String, Object> importMeta = new LinkedHashMap<>();
        putOptional(importMeta, "activity_category", getOptionalString(row, headers, "Activity category"));
        putOptional(importMeta, "common",            getOptionalString(row, headers, "Common"));
        putOptional(importMeta, "dependencies",      getOptionalString(row, headers, "Dependencies"));
        putOptional(importMeta, "validation",        getOptionalString(row, headers, "Validation"));

        rows.add(new ParsedTaskRow(
                projectId, projectName, taskGroupId, taskGroupName,
                stepSeq, taskName, executionType, critical,
                inputParams.isEmpty() ? null : inputParams,
                expectedOutput, owner, plannedStart, plannedEnd,
                importMeta.isEmpty() ? null : importMeta
        ));
    }

    private Integer parseStepSeq(Row row, Map<String, Integer> headers, int excelRow, List<ImportError> errors) {
        if (!headers.containsKey("Step seq#")) {
            errors.add(new ImportError(excelRow, "Step seq#", "Column not found in sheet"));
            return null;
        }
        String val = getCellString(row, headers.get("Step seq#")).trim();
        if (val.isEmpty()) {
            errors.add(new ImportError(excelRow, "Step seq#", "Required field is missing or blank"));
            return null;
        }
        try {
            int seq = Integer.parseInt(val);
            if (seq <= 0) {
                errors.add(new ImportError(excelRow, "Step seq#", "Must be a positive integer"));
                return null;
            }
            return seq;
        } catch (NumberFormatException e) {
            errors.add(new ImportError(excelRow, "Step seq#", "Must be a positive integer, got: " + val));
            return null;
        }
    }

    private ExecutionType parseExecutionType(String raw, int excelRow, List<ImportError> errors) {
        if (raw == null) return null; // already recorded as missing by getRequired
        try {
            return ExecutionType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            errors.add(new ImportError(excelRow, "Execution Type",
                    "Must be MANUAL or AUTO, got: " + raw));
            return null;
        }
    }

    private boolean parseCritical(Row row, Map<String, Integer> headers, int excelRow, List<ImportError> errors) {
        if (!headers.containsKey("Critical")) {
            return false;
        }

        String raw = getOptionalString(row, headers, "Critical");
        if (raw == null) {
            return false;
        }

        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "Y", "YES", "TRUE" -> true;
            case "N", "NO", "FALSE" -> false;
            default -> {
                errors.add(new ImportError(excelRow, "Critical", "Must be Y or N, got: " + raw));
                yield false;
            }
        };
    }

    // ─── Field helpers ────────────────────────────────────────────────────────

    private String getRequired(Row row, Map<String, Integer> headers, String col,
                                int rowNum, List<ImportError> errors) {
        if (!headers.containsKey(col)) {
            errors.add(new ImportError(rowNum, col, "Column not found in sheet"));
            return null;
        }
        String val = getCellString(row, headers.get(col)).trim();
        if (val.isEmpty()) {
            errors.add(new ImportError(rowNum, col, "Required field is missing or blank"));
            return null;
        }
        return val;
    }

    private String getOptionalString(Row row, Map<String, Integer> headers, String col) {
        if (!headers.containsKey(col)) return null;
        String val = getCellString(row, headers.get(col)).trim();
        return val.isEmpty() ? null : val;
    }

    private Instant getOptionalDate(Row row, Map<String, Integer> headers, String col) {
        if (!headers.containsKey(col)) return null;
        Cell cell = row.getCell(headers.get(col));
        if (cell == null || cell.getCellType() == CellType.BLANK) return null;
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toInstant(ZoneOffset.UTC);
        }
        String str = getCellStringValue(cell).trim();
        if (str.isEmpty()) return null;
        try { return Instant.parse(str); } catch (Exception e) { return null; }
    }

    private String getCellString(Row row, int colIdx) {
        Cell cell = row.getCell(colIdx);
        return cell == null ? "" : getCellStringValue(cell);
    }

    private String getCellStringValue(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toString()
                    : String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try { yield cell.getStringCellValue(); }
                catch (Exception e) { yield String.valueOf((long) cell.getNumericCellValue()); }
            }
            default -> "";
        };
    }

    private Map<String, Integer> buildHeaderIndex(Row headerRow) {
        Map<String, Integer> idx = new LinkedHashMap<>();
        for (Cell cell : headerRow) {
            String name = getCellStringValue(cell).trim();
            if (!name.isEmpty()) idx.put(name, cell.getColumnIndex());
        }
        return idx;
    }

    private boolean isBlankRow(Row row, Map<String, Integer> headers) {
        return headers.values().stream().allMatch(ci -> {
            Cell c = row.getCell(ci);
            return c == null || c.getCellType() == CellType.BLANK || getCellStringValue(c).isBlank();
        });
    }

    private void putOptional(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) map.put(key, value);
    }
}
