package com.wwa.deploymentagent.domain.fileimport;

public record ImportError(int row, String column, String message) {}
