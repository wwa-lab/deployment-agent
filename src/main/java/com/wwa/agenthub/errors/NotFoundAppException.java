package com.wwa.agenthub.errors;

public class NotFoundAppException extends AppException {
    public NotFoundAppException(String resource, String id) {
        super("NOT_FOUND", 404, resource + " not found: " + id);
    }
}
