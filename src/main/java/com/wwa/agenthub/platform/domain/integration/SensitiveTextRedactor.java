package com.wwa.agenthub.platform.domain.integration;

import java.util.regex.Pattern;

/**
 * Removes common credential forms from client-originated operational text before persistence.
 */
public final class SensitiveTextRedactor {

    private static final String REDACTED = "[REDACTED]";
    private static final String UNSAFE_OPERATIONAL_TEXT = "[REDACTED UNSAFE OPERATIONAL CONTENT]";
    private static final Pattern AUTHORIZATION_HEADER = Pattern.compile(
            "(?im)(\\b(?:proxy-)?authorization\\b\\s*[:=]\\s*)[^\\r\\n]*");
    private static final Pattern NAMED_SECRET = Pattern.compile(
            "(?i)(\\b(?:authorization|access[_-]?token|api[_-]?(?:token|key)|refresh[_-]?token|"
                    + "id[_-]?token|token|password|passwd|secret|client[_-]?secret|cookie|set-cookie|"
                    + "session(?:id)?|database[_-]?url|connection[_-]?(?:string|url)|private[_-]?key)"
                    + "\\b\\s*[:=]\\s*)"
                    + "(?:Bearer\\s+)?(?:\"[^\"\\r\\n]*\"|'[^'\\r\\n]*'|[^\\s,;]+)");
    private static final Pattern URI_CREDENTIAL = Pattern.compile(
            "(?i)(\\b[a-z][a-z0-9+.-]*://[^\\s:/@]+:)[^\\s/@]+(@)");
    private static final Pattern SECRET_QUERY_PARAMETER = Pattern.compile(
            "(?i)([?&](?:access_token|api_key|token|password|secret)=)[^&#\\s]+");
    private static final Pattern BEARER_SECRET = Pattern.compile(
            "(?i)(\\bBearer\\s+)[A-Za-z0-9._~+/=-]{8,}");
    private static final Pattern JWT = Pattern.compile(
            "\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b");
    private static final Pattern WELL_KNOWN_SECRET = Pattern.compile(
            "\\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|"
                    + "sk-[A-Za-z0-9_-]{16,}|AKIA[0-9A-Z]{16})\\b");
    private static final Pattern PRIVATE_KEY = Pattern.compile(
            "(?is)-----BEGIN [A-Z ]*PRIVATE KEY-----.*?(?:-----END [A-Z ]*PRIVATE KEY-----|$)");
    private static final Pattern SOURCE_LIKE_TEXT = Pattern.compile(
            "(?im)^\\s*(?:package\\s+[A-Za-z_]|import\\s+[A-Za-z_].*;|#include\\s*[<\"]|"
                    + "(?:(?:public|private|protected)\\s+)?(?:final\\s+)?"
                    + "(?:class|interface|enum|record)\\s+[A-Za-z_]|"
                    + "(?:def|func|function)\\s+[A-Za-z_][A-Za-z0-9_]*|"
                    + "(?:const|let|var)\\s+[A-Za-z_$][A-Za-z0-9_$]*\\s*=|"
                    + "(?:CREATE|ALTER|DROP)\\s+(?:TABLE|VIEW|INDEX|PROCEDURE|FUNCTION)\\s+|"
                    + "SELECT\\s+.+\\s+FROM\\s+)");
    private static final Pattern STRUCTURED_OR_ADDITIONAL_SOURCE = Pattern.compile(
            "(?im)^\\s*(?:```|#!|[<{\\[]|"
                    + "(?:apiVersion|kind|metadata|spec|services|jobs|steps|stages|variables|resources|"
                    + "dependencies|hosts|tasks|playbook)\\s*:|"
                    + "-(?:\\s+)(?:name|run|uses|script|task|job)\\s*:|"
                    + "(?!(?:error|warning|reason|status|message|failure|note|result|token|password|secret)\\b)"
                    + "[A-Za-z_][A-Za-z0-9_.-]{0,63}\\s*[:=]\\s*(?:[\"'{\\[]|\\w+\\.|true\\b|false\\b|\\d)|"
                    + "(?:resource|data|provider|module|variable|output|terraform|locals)\\b.*\\{|"
                    + "(?:FROM|RUN|COPY|ADD|CMD|ENTRYPOINT|ENV|ARG|WORKDIR|EXPOSE|USER|VOLUME)\\s+|"
                    + "(?:IDENTIFICATION|ENVIRONMENT|DATA|PROCEDURE)\\s+DIVISION\\.|"
                    + "(?:WORKING-STORAGE|LINKAGE)\\s+SECTION\\.|"
                    + "\\*\\*FREE\\b|CTL-OPT\\b|DCL-[SPCR]\\b|BEGSR\\b|ENDSR\\b)");
    private static final Pattern SAFE_EVIDENCE_CHARACTERS = Pattern.compile(
            "^[\\p{L}\\p{N}\\p{Zs}\\t\\r\\n.,:!?%+_/@'\\-]*$");
    private static final Pattern CODE_STATEMENT_PREFIX = Pattern.compile(
            "(?im)^\\s*(?:return|print|printf|echo|int|long|short|float|double|void|char|byte|"
                    + "boolean|bool|string|class|record|public|private|protected|function|def|"
                    + "package|import|from|using|namespace|if|for|while|switch|case|try|catch|"
                    + "throw|new|const|let|var)\\b");
    private static final Pattern RAW_LOG_LINE = Pattern.compile(
            "(?im)^\\s*(?:(?:\\d{4}-\\d{2}-\\d{2}[T\\s]\\d{2}:\\d{2}:\\d{2}"
                    + "(?:[.,]\\d+)?(?:Z|[+-]\\d{2}:?\\d{2})?)\\s+)?"
                    + "(?:TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL)\\b");
    private static final Pattern ISO_TIMESTAMPED_LINE = Pattern.compile(
            "(?m)^\\s*\\d{4}-\\d{2}-\\d{2}[T\\s]\\d{2}:\\d{2}:\\d{2}"
                    + "(?:[.,]\\d+)?(?:Z|[+-]\\d{2}:?\\d{2})?(?:\\s|$)");
    private static final Pattern SYSLOG_LINE = Pattern.compile(
            "(?im)^\\s*(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)"
                    + "\\s+\\d{1,2}\\s+\\d{2}:\\d{2}:\\d{2}\\s+\\S+");
    private static final Pattern STACK_TRACE_LINE = Pattern.compile(
            "(?im)^\\s*(?:at\\s+[A-Za-z_$][A-Za-z0-9_.$]*\\(|"
                    + "Caused by:|Suppressed:|Exception in thread)");
    private static final Pattern MARKDOWN_CODE_FENCE = Pattern.compile("(?m)^\\s*(?:```|~~~)");
    private static final Pattern MARKDOWN_LINE_PREFIX = Pattern.compile(
            "(?m)^\\s{0,3}(?:#{1,6}\\s+|>\\s*|[-+*]\\s+|\\d+[.)]\\s+)");
    private static final Pattern MARKDOWN_LINK = Pattern.compile(
            "!?\\[([^]\\r\\n]*)]\\([^\\r\\n)]*\\)");
    private static final Pattern MARKDOWN_CHECKBOX = Pattern.compile("\\[[ xX]]\\s*");
    private static final Pattern MARKDOWN_MARKERS = Pattern.compile("[*_~`|]");

    private SensitiveTextRedactor() {
    }

    public static String redact(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String redacted = PRIVATE_KEY.matcher(value).replaceAll(REDACTED);
        redacted = AUTHORIZATION_HEADER.matcher(redacted).replaceAll("$1" + REDACTED);
        redacted = NAMED_SECRET.matcher(redacted).replaceAll("$1" + REDACTED);
        redacted = URI_CREDENTIAL.matcher(redacted).replaceAll("$1" + REDACTED + "$2");
        redacted = SECRET_QUERY_PARAMETER.matcher(redacted).replaceAll("$1" + REDACTED);
        redacted = BEARER_SECRET.matcher(redacted).replaceAll("$1" + REDACTED);
        redacted = JWT.matcher(redacted).replaceAll(REDACTED);
        return WELL_KNOWN_SECRET.matcher(redacted).replaceAll(REDACTED);
    }

    public static String redact(String value, int maximumLength) {
        String redacted = redact(value);
        if (redacted == null || redacted.length() <= maximumLength) {
            return redacted;
        }
        return redacted.substring(0, Math.max(0, maximumLength));
    }

    /** Safe projection for client-authored text that may be shown in Web/API views. */
    public static String redactOperational(String value, int maximumLength) {
        String redacted = redact(value, maximumLength);
        if (redacted != null
                && (redacted.indexOf('\n') >= 0
                || redacted.indexOf('\r') >= 0
                || SOURCE_LIKE_TEXT.matcher(redacted).find()
                || STRUCTURED_OR_ADDITIONAL_SOURCE.matcher(redacted).find())) {
            return UNSAFE_OPERATIONAL_TEXT;
        }
        return redacted;
    }

    /**
     * Positive, deliberately narrow policy for copied textual evidence.
     * Rich logs, structured configuration, and source belong in an external
     * approved provider; Atlas accepts only bounded prose/report text here.
     */
    public static boolean isSafeEvidenceText(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return redact(value).equals(value)
                && SAFE_EVIDENCE_CHARACTERS.matcher(value).matches()
                && !SOURCE_LIKE_TEXT.matcher(value).find()
                && !STRUCTURED_OR_ADDITIONAL_SOURCE.matcher(value).find()
                && !CODE_STATEMENT_PREFIX.matcher(value).find()
                && !RAW_LOG_LINE.matcher(value).find()
                && !ISO_TIMESTAMPED_LINE.matcher(value).find()
                && !SYSLOG_LINE.matcher(value).find()
                && !STACK_TRACE_LINE.matcher(value).find();
    }

    /**
     * Allows common prose-only Markdown while rejecting fenced code, secrets,
     * source statements, and characters left behind by executable snippets.
     */
    public static boolean isSafeMarkdownEvidenceText(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        if (MARKDOWN_CODE_FENCE.matcher(value).find() || !redact(value).equals(value)) {
            return false;
        }
        String prose = MARKDOWN_LINK.matcher(value).replaceAll("$1");
        prose = MARKDOWN_LINE_PREFIX.matcher(prose).replaceAll("");
        prose = MARKDOWN_CHECKBOX.matcher(prose).replaceAll("");
        prose = MARKDOWN_MARKERS.matcher(prose).replaceAll("");
        return isSafeEvidenceText(prose);
    }
}
