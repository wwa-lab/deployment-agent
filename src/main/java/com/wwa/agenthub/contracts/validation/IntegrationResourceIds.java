package com.wwa.agenthub.contracts.validation;

import java.util.regex.Pattern;

/**
 * Shared validation for identifiers exposed through the Atlas Execution API.
 */
public final class IntegrationResourceIds {

    public static final String REGEX = "^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$";

    private static final Pattern PATTERN = Pattern.compile(REGEX);

    private IntegrationResourceIds() {
    }

    public static boolean isValid(String value) {
        return value != null && PATTERN.matcher(value).matches();
    }
}
