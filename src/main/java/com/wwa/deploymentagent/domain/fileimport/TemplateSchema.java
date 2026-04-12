package com.wwa.deploymentagent.domain.fileimport;

import java.util.List;

/**
 * Describes a single XLSX upload template: sheet name, downloadable file name,
 * the ordered list of column headers that appear in row 0, the matching sample
 * row rendered in row 1, and the subset of headers whose values should flow
 * into {@code Task.customFields} rather than being read by the shared parser.
 *
 * <p>This is a <b>descriptor</b>, not a full parsing DSL. The day-1 parser
 * ({@link ExcelParserService}) still applies hardcoded field-level validation
 * for the shared default schema. The schema exists so that both the template
 * generator ({@link UploadTemplateService}) and the parser can agree on a
 * single source of truth for which columns belong in which agent's template
 * — and so that future per-agent customization (adding extra columns beyond
 * the shared core) becomes a single-file change registered in
 * {@link TemplateSchemaRegistry} instead of a coordinated edit across
 * generator, parser, and schema.
 *
 * <p>MVP Foundation Seam — see docs/04-architecture/architecture.md
 * §MVP Foundation Seams.
 */
public record TemplateSchema(
        String sheetName,
        String fileName,
        List<String> headers,
        List<String> sampleRow,
        List<String> customFieldColumns
) {
    public TemplateSchema {
        if (headers == null || headers.isEmpty()) {
            throw new IllegalArgumentException("TemplateSchema.headers must not be empty");
        }
        if (sampleRow != null && sampleRow.size() > headers.size()) {
            throw new IllegalArgumentException(
                    "TemplateSchema.sampleRow has more cells than headers");
        }
        // Defensive copies to preserve immutability
        headers = List.copyOf(headers);
        sampleRow = sampleRow == null ? List.of() : List.copyOf(sampleRow);
        customFieldColumns = customFieldColumns == null
                ? List.of()
                : List.copyOf(customFieldColumns);
    }
}
