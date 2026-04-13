package com.wwa.agenthub.domain.fileimport;

public record ImportError(int row, String column, String message) {}
