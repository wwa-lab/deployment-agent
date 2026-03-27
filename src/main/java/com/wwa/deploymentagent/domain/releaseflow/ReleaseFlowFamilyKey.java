package com.wwa.deploymentagent.domain.releaseflow;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes release identifiers into a stage-neutral family key so summary views can stitch
 * together SIT/UAT/PROD uploads that belong to the same logical rollout.
 */
public final class ReleaseFlowFamilyKey {

    private static final Pattern STAGE_PREFIX_WITH_SEPARATOR = Pattern.compile(
            "^(sit|uat|prod)([^a-z0-9]+)(.+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern STAGE_PREFIX_WITH_DIGITS = Pattern.compile(
            "^(sit|uat|prod)(\\d.+)$",
            Pattern.CASE_INSENSITIVE);

    private ReleaseFlowFamilyKey() {}

    public static String fromIdentifier(String releaseIdentifier) {
        String normalized = normalizeAlphaNumeric(stripStagePrefix(releaseIdentifier));
        if (!normalized.isBlank()) {
            return normalized;
        }
        return normalizeAlphaNumeric(releaseIdentifier);
    }

    public static String fromStoredRelease(String releaseIdentifier, String normalizedReleaseIdentifier) {
        String normalizedFromReleaseId = normalizeAlphaNumeric(stripStagePrefix(releaseIdentifier));
        if (!normalizedFromReleaseId.isBlank()) {
            return normalizedFromReleaseId;
        }

        String normalizedFallback = normalizeAlphaNumeric(normalizedReleaseIdentifier);
        if (!normalizedFallback.isBlank()) {
            return stripStagePrefixFromNormalized(normalizedFallback);
        }

        return "";
    }

    public static String legacyIdentifier(String releaseIdentifier) {
        return releaseIdentifier == null ? "" : releaseIdentifier.trim().toLowerCase(Locale.ROOT);
    }

    private static String stripStagePrefix(String releaseIdentifier) {
        if (releaseIdentifier == null) {
            return "";
        }

        String normalized = releaseIdentifier.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return "";
        }

        Matcher separatorMatcher = STAGE_PREFIX_WITH_SEPARATOR.matcher(normalized);
        if (separatorMatcher.matches()) {
            return separatorMatcher.group(3);
        }

        Matcher digitMatcher = STAGE_PREFIX_WITH_DIGITS.matcher(normalized);
        if (digitMatcher.matches()) {
            return digitMatcher.group(2);
        }

        return normalized;
    }

    private static String stripStagePrefixFromNormalized(String normalizedReleaseIdentifier) {
        if (normalizedReleaseIdentifier == null || normalizedReleaseIdentifier.isBlank()) {
            return "";
        }

        String normalized = normalizedReleaseIdentifier.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("sit") && normalized.length() > 3 && Character.isDigit(normalized.charAt(3))) {
            return normalized.substring(3);
        }
        if (normalized.startsWith("uat") && normalized.length() > 3 && Character.isDigit(normalized.charAt(3))) {
            return normalized.substring(3);
        }
        if (normalized.startsWith("prod") && normalized.length() > 4 && Character.isDigit(normalized.charAt(4))) {
            return normalized.substring(4);
        }
        return normalized;
    }

    private static String normalizeAlphaNumeric(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
