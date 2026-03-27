package com.wwa.deploymentagent.domain.releaseflow;

import java.util.ArrayList;
import java.util.Arrays;
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
        String normalized = normalizeAlphaNumeric(stripStageToken(releaseIdentifier));
        if (!normalized.isBlank()) {
            return normalized;
        }
        return normalizeAlphaNumeric(releaseIdentifier);
    }

    public static String fromStoredRelease(String releaseIdentifier, String normalizedReleaseIdentifier) {
        String normalizedFromReleaseId = normalizeAlphaNumeric(stripStageToken(releaseIdentifier));
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

    private static String stripStageToken(String releaseIdentifier) {
        if (releaseIdentifier == null) {
            return "";
        }

        String normalized = releaseIdentifier.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return "";
        }

        String infixNormalized = stripInfixStageToken(normalized);
        if (!infixNormalized.equals(normalized)) {
            return infixNormalized;
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

    private static String stripInfixStageToken(String normalizedReleaseIdentifier) {
        String[] tokens = Arrays.stream(normalizedReleaseIdentifier.split("[^a-z0-9]+"))
                .filter(token -> token != null && !token.isBlank())
                .toArray(String[]::new);
        if (tokens.length < 3) {
            return normalizedReleaseIdentifier;
        }

        int firstStageTokenIndex = -1;
        for (int idx = 0; idx < tokens.length; idx++) {
            if (isStageToken(tokens[idx])) {
                firstStageTokenIndex = idx;
                break;
            }
        }
        if (firstStageTokenIndex <= 0 || firstStageTokenIndex >= tokens.length - 1) {
            return normalizedReleaseIdentifier;
        }

        ArrayList<String> remainingTokens = new ArrayList<>();
        for (String token : tokens) {
            if (!isStageToken(token)) {
                remainingTokens.add(token);
            }
        }
        if (remainingTokens.isEmpty()) {
            return normalizedReleaseIdentifier;
        }

        if (isDigitsOnly(remainingTokens.get(remainingTokens.size() - 1))
                && remainingTokens.size() > 1
                && remainingTokens.subList(0, remainingTokens.size() - 1).stream()
                .anyMatch(ReleaseFlowFamilyKey::containsAlpha)) {
            remainingTokens.remove(remainingTokens.size() - 1);
        }

        return String.join("", remainingTokens);
    }

    private static boolean isStageToken(String token) {
        return "sit".equals(token) || "uat".equals(token) || "prod".equals(token);
    }

    private static boolean isDigitsOnly(String value) {
        return value != null && !value.isBlank() && value.chars().allMatch(Character::isDigit);
    }

    private static boolean containsAlpha(String value) {
        return value != null && value.chars().anyMatch(Character::isLetter);
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
