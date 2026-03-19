package com.wwa.deploymentagent.errors;

import com.wwa.deploymentagent.domain.fileimport.ImportError;

import java.util.List;

public class ImportValidationException extends RuntimeException {

    private final List<ImportError> errors;

    public ImportValidationException(List<ImportError> errors) {
        super("Excel import validation failed with " + errors.size() + " error(s)");
        this.errors = errors;
    }

    public List<ImportError> getErrors() { return errors; }
}
