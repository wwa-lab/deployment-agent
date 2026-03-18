package com.wwa.deploymentagent.domain.fileimport;

import java.util.List;

public record ParseResult(List<ParsedTaskRow> rows, List<ImportError> errors) {
    public boolean hasErrors() { return !errors.isEmpty(); }
}
