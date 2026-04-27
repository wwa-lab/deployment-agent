package com.wwa.agenthub.domain.filecompare;

import com.wwa.agenthub.contracts.dto.CsvCompareFileResultDto;
import com.wwa.agenthub.contracts.dto.CsvCompareResponseDto;
import com.wwa.agenthub.contracts.dto.CsvDifferenceDto;
import com.wwa.agenthub.errors.ValidationAppException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class CsvCompareService {

    private static final int MAX_DIFFERENCE_SAMPLES = 1_000;

    public CsvCompareResponseDto compare(List<MultipartFile> files) throws IOException {
        if (files == null || files.size() < 2) {
            throw new ValidationAppException("Upload at least two CSV files to compare.");
        }

        List<MultipartFile> normalizedFiles = files.stream()
                .filter(Objects::nonNull)
                .toList();
        if (normalizedFiles.size() < 2) {
            throw new ValidationAppException("Upload at least two CSV files to compare.");
        }
        for (MultipartFile file : normalizedFiles) {
            validateCsvFile(file);
        }

        MultipartFile baseFile = normalizedFiles.getFirst();
        List<CsvCompareFileResultDto> comparisons = new ArrayList<>();
        for (int index = 1; index < normalizedFiles.size(); index++) {
            comparisons.add(comparePair(baseFile, normalizedFiles.get(index)));
        }

        long totalDifferences = comparisons.stream()
                .mapToLong(CsvCompareFileResultDto::totalDifferences)
                .sum();
        boolean truncated = comparisons.stream()
                .anyMatch(CsvCompareFileResultDto::truncated);

        return new CsvCompareResponseDto(
                baseFile.getOriginalFilename(),
                normalizedFiles.size(),
                comparisons,
                totalDifferences,
                truncated);
    }

    private CsvCompareFileResultDto comparePair(MultipartFile baseFile, MultipartFile targetFile)
            throws IOException {
        try (CsvCursor base = CsvCursor.open(baseFile);
             CsvCursor target = CsvCursor.open(targetFile)) {

            List<String> baseHeader = base.readRequiredHeader();
            List<String> targetHeader = target.readRequiredHeader();
            if (!baseHeader.equals(targetHeader)) {
                throw new ValidationAppException(
                        "CSV headers must match. File '" + targetFile.getOriginalFilename()
                                + "' has different headers from base file '"
                                + baseFile.getOriginalFilename() + "'.");
            }

            List<CsvDifferenceDto> differences = new ArrayList<>();
            long totalDifferences = 0;
            long matchedRows = 0;
            long addedRows = 0;
            long removedRows = 0;
            long changedRows = 0;
            long dataLineNumber = 1;

            while (true) {
                List<String> baseRow = base.readRow();
                List<String> targetRow = target.readRow();
                if (baseRow == null && targetRow == null) {
                    break;
                }

                dataLineNumber++;
                if (baseRow == null) {
                    addedRows++;
                    totalDifferences++;
                    addDifference(differences, new CsvDifferenceDto(
                            dataLineNumber,
                            "ADDED",
                            null,
                            null,
                            null,
                            null,
                            targetRow));
                    continue;
                }
                if (targetRow == null) {
                    removedRows++;
                    totalDifferences++;
                    addDifference(differences, new CsvDifferenceDto(
                            dataLineNumber,
                            "REMOVED",
                            null,
                            null,
                            null,
                            baseRow,
                            null));
                    continue;
                }

                int maxColumns = Math.max(baseRow.size(), targetRow.size());
                boolean rowChanged = false;
                for (int columnIndex = 0; columnIndex < maxColumns; columnIndex++) {
                    String left = columnValue(baseRow, columnIndex);
                    String right = columnValue(targetRow, columnIndex);
                    if (!Objects.equals(left, right)) {
                        rowChanged = true;
                        totalDifferences++;
                        addDifference(differences, new CsvDifferenceDto(
                                dataLineNumber,
                                "CHANGED",
                                columnIndex < baseHeader.size() ? baseHeader.get(columnIndex) : "column_" + (columnIndex + 1),
                                left,
                                right,
                                null,
                                null));
                    }
                }

                if (rowChanged) {
                    changedRows++;
                } else {
                    matchedRows++;
                }
            }

            return new CsvCompareFileResultDto(
                    targetFile.getOriginalFilename(),
                    baseHeader,
                    matchedRows,
                    changedRows,
                    addedRows,
                    removedRows,
                    totalDifferences,
                    totalDifferences > differences.size(),
                    differences);
        }
    }

    private void addDifference(List<CsvDifferenceDto> differences, CsvDifferenceDto difference) {
        if (differences.size() < MAX_DIFFERENCE_SAMPLES) {
            differences.add(difference);
        }
    }

    private String columnValue(List<String> row, int columnIndex) {
        return columnIndex < row.size() ? row.get(columnIndex) : null;
    }

    private void validateCsvFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ValidationAppException("Uploaded CSV file is empty: " + file.getOriginalFilename());
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            throw new ValidationAppException("Only .csv files can be compared.");
        }
    }

    private static final class CsvCursor implements AutoCloseable {
        private final MultipartFile file;
        private final BufferedReader reader;

        private CsvCursor(MultipartFile file, BufferedReader reader) {
            this.file = file;
            this.reader = reader;
        }

        static CsvCursor open(MultipartFile file) throws IOException {
            return new CsvCursor(
                    file,
                    new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)));
        }

        List<String> readRequiredHeader() throws IOException {
            List<String> header = readRow();
            if (header == null || header.isEmpty()) {
                throw new ValidationAppException("CSV header is missing: " + file.getOriginalFilename());
            }
            return header;
        }

        List<String> readRow() throws IOException {
            String line = readCsvRecord();
            if (line == null) {
                return null;
            }
            return parseLine(line);
        }

        private String readCsvRecord() throws IOException {
            String line = reader.readLine();
            if (line == null) {
                return null;
            }
            StringBuilder record = new StringBuilder(line);
            while (hasOpenQuote(record)) {
                String continuation = reader.readLine();
                if (continuation == null) {
                    break;
                }
                record.append('\n').append(continuation);
            }
            return record.toString();
        }

        private boolean hasOpenQuote(CharSequence value) {
            boolean quoted = false;
            for (int index = 0; index < value.length(); index++) {
                char current = value.charAt(index);
                if (current == '"') {
                    if (quoted && index + 1 < value.length() && value.charAt(index + 1) == '"') {
                        index++;
                    } else {
                        quoted = !quoted;
                    }
                }
            }
            return quoted;
        }

        private List<String> parseLine(String line) {
            List<String> values = new ArrayList<>();
            StringBuilder value = new StringBuilder();
            boolean quoted = false;
            for (int index = 0; index < line.length(); index++) {
                char current = line.charAt(index);
                if (current == '"') {
                    if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                        value.append('"');
                        index++;
                    } else {
                        quoted = !quoted;
                    }
                } else if (current == ',' && !quoted) {
                    values.add(value.toString());
                    value.setLength(0);
                } else {
                    value.append(current);
                }
            }
            values.add(value.toString());
            return values;
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }
    }
}
