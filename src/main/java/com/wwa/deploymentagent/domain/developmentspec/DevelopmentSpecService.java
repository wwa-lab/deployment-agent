package com.wwa.deploymentagent.domain.developmentspec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.dto.DevelopmentSpecDto;
import com.wwa.deploymentagent.contracts.enums.AuditActionType;
import com.wwa.deploymentagent.contracts.enums.DevelopmentSpecStatus;
import com.wwa.deploymentagent.domain.audit.AuditLoggerService;
import com.wwa.deploymentagent.errors.ForbiddenAppException;
import com.wwa.deploymentagent.errors.NotFoundAppException;
import com.wwa.deploymentagent.errors.ValidationAppException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DevelopmentSpecService {

    private final DevelopmentSpecRepository developmentSpecRepository;
    private final AuditLoggerService auditLoggerService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Page<DevelopmentSpec> list(String query,
                                      DevelopmentSpecStatus status,
                                      Pageable pageable,
                                      UserContext user) {
        validateAuthenticatedUser(user, "list_development_specs");

        String normalizedQuery = normalizeSearchQuery(query);
        List<DevelopmentSpec> filtered = developmentSpecRepository.findAll(Sort.by(Sort.Direction.DESC, "updatedAt")).stream()
                .filter(spec -> status == null || spec.getStatus() == status)
                .filter(spec -> normalizedQuery == null || matchesQuery(spec, normalizedQuery))
                .filter(spec -> canAccess(user, spec))
                .toList();

        int start = Math.toIntExact(pageable.getOffset());
        if (start >= filtered.size()) {
            return new PageImpl<>(List.of(), pageable, filtered.size());
        }

        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        return new PageImpl<>(filtered.subList(start, end), pageable, filtered.size());
    }

    @Transactional(readOnly = true)
    public DevelopmentSpec get(String id, UserContext user) {
        validateAuthenticatedUser(user, "view_development_spec");
        DevelopmentSpec spec = developmentSpecRepository.findById(id)
                .orElseThrow(() -> new NotFoundAppException("DevelopmentSpec", id));
        validateScopeAccess(user, spec, "view_development_spec");
        return spec;
    }

    @Transactional
    public DevelopmentSpec create(DevelopmentSpecDto.UpsertRequest request, UserContext user) {
        validateEditor(user, "create_development_spec");
        validateRequest(request);
        validateRequestScope(user, request.application(), request.snowGroup(), "create_development_spec");

        DevelopmentSpec spec = new DevelopmentSpec();
        applyUpsert(spec, request, user.userId(), false);

        DevelopmentSpec saved = developmentSpecRepository.save(spec);
        auditLoggerService.log(user, AuditActionType.development_spec_create, buildAuditContext(saved));
        return saved;
    }

    @Transactional
    public DevelopmentSpec update(String id, DevelopmentSpecDto.UpsertRequest request, UserContext user) {
        validateEditor(user, "update_development_spec");
        validateRequest(request);

        DevelopmentSpec spec = developmentSpecRepository.findById(id)
                .orElseThrow(() -> new NotFoundAppException("DevelopmentSpec", id));
        validateScopeAccess(user, spec, "update_development_spec");
        validateRequestScope(user, request.application(), request.snowGroup(), "update_development_spec");
        validateVersionMatch(id, request.version(), spec.getVersion());

        applyUpsert(spec, request, user.userId(), true);

        DevelopmentSpec saved = developmentSpecRepository.save(spec);
        auditLoggerService.log(user, AuditActionType.development_spec_update, buildAuditContext(saved));
        return saved;
    }

    @Transactional
    public DevelopmentSpec generate(String id, UserContext user) {
        validateEditor(user, "generate_development_spec");

        DevelopmentSpec spec = developmentSpecRepository.findById(id)
                .orElseThrow(() -> new NotFoundAppException("DevelopmentSpec", id));
        validateScopeAccess(user, spec, "generate_development_spec");
        validateSpecState(spec);

        Map<String, Object> normalizedSource = normalizeSourcePayload(spec.getSourcePayload());
        Map<String, Object> generatedPayload = buildGeneratedPayload(spec, normalizedSource);
        String generatedContent = buildGeneratedContent(generatedPayload);

        spec.setGeneratedPayload(generatedPayload);
        spec.setGeneratedContent(generatedContent);
        spec.setGeneratedAt(Instant.now());
        spec.setGeneratedBy(user.userId());
        spec.setStatus(DevelopmentSpecStatus.GENERATED);
        spec.setUpdatedBy(user.userId());

        DevelopmentSpec saved = developmentSpecRepository.save(spec);
        auditLoggerService.log(user, AuditActionType.development_spec_generate, buildAuditContext(saved));
        return saved;
    }

    @Transactional(readOnly = true)
    public ExportDocument export(String id, String format, UserContext user) {
        DevelopmentSpec spec = get(id, user);
        if (spec.getStatus() != DevelopmentSpecStatus.GENERATED
                || spec.getGeneratedContent() == null
                || spec.getGeneratedPayload() == null) {
            throw new ValidationAppException("Development Spec must be generated before export.");
        }

        String normalizedFormat = normalizeExportFormat(format);
        ExportDocument document = switch (normalizedFormat) {
            case "markdown" -> new ExportDocument(
                    buildExportFilename(spec, "md"),
                    "text/markdown",
                    spec.getGeneratedContent().getBytes(StandardCharsets.UTF_8)
            );
            case "json" -> new ExportDocument(
                    buildExportFilename(spec, "json"),
                    "application/json",
                    buildExportJson(spec)
            );
            default -> throw new ValidationAppException("Unsupported export format: " + format + ".");
        };
        auditLoggerService.log(user, AuditActionType.development_spec_export, buildAuditContext(spec, normalizedFormat));
        return document;
    }

    private void applyUpsert(DevelopmentSpec spec,
                             DevelopmentSpecDto.UpsertRequest request,
                             String operatorId,
                             boolean preserveGeneratedOutput) {
        spec.setTitle(request.title().trim());
        spec.setModuleName(normalizeBlank(request.moduleName()));
        spec.setProgramType(request.programType().name());
        spec.setCodeStyle(request.codeStyle().name());
        spec.setApplication(request.application().trim());
        spec.setSnowGroup(request.snowGroup().trim());
        spec.setSourcePayload(normalizeSourcePayload(request.sourcePayload()));
        spec.setUpdatedBy(operatorId);

        if (spec.getCreatedBy() == null) {
            spec.setCreatedBy(operatorId);
        }

        if (!preserveGeneratedOutput) {
            spec.setGeneratedPayload(null);
            spec.setGeneratedContent(null);
            spec.setGeneratedAt(null);
            spec.setGeneratedBy(null);
            spec.setStatus(DevelopmentSpecStatus.DRAFT);
            return;
        }

        spec.setGeneratedPayload(null);
        spec.setGeneratedContent(null);
        spec.setGeneratedAt(null);
        spec.setGeneratedBy(null);
        spec.setStatus(DevelopmentSpecStatus.DRAFT);
    }

    private void validateRequest(DevelopmentSpecDto.UpsertRequest request) {
        if (request == null) {
            throw new ValidationAppException("Development Spec request is required.");
        }
        validateTitle(request.title());
        validateScopeFields(request.application(), request.snowGroup());
        validatePayloadObjectives(request.sourcePayload());
    }

    private void validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new ValidationAppException("Development Spec title is required.");
        }
    }

    private void validateScopeFields(String application, String snowGroup) {
        if (application == null || application.isBlank()) {
            throw new ValidationAppException("Application is required.");
        }
        if (snowGroup == null || snowGroup.isBlank()) {
            throw new ValidationAppException("SNOW Group is required.");
        }
    }

    private void validatePayloadObjectives(Map<String, Object> payload) {
        Map<String, Object> normalizedPayload = normalizeSourcePayload(payload);
        if (!hasMeaningfulValue(normalizedPayload.get("businessObjective"))
                && !hasMeaningfulValue(normalizedPayload.get("businessObjectives"))
                && !hasMeaningfulValue(normalizedPayload.get("implementationObjective"))
                && !hasMeaningfulValue(normalizedPayload.get("implementationObjectives"))) {
            throw new ValidationAppException(
                    "At least one business objective or implementation objective is required.");
        }
    }

    private void validateSpecState(DevelopmentSpec spec) {
        validateTitle(spec.getTitle());
        validateScopeFields(spec.getApplication(), spec.getSnowGroup());
        validatePayloadObjectives(spec.getSourcePayload());
    }

    private void validateVersionMatch(String id, Long requestVersion, Long currentVersion) {
        if (requestVersion == null) {
            throw new ValidationAppException("Version is required for Development Spec update.");
        }
        if (!Objects.equals(requestVersion, currentVersion)) {
            throw new ValidationAppException("Version mismatch for Development Spec update.", Map.of(
                    "id", id,
                    "expectedVersion", currentVersion,
                    "requestVersion", requestVersion
            ));
        }
    }

    private void validateAuthenticatedUser(UserContext user, String action) {
        if (user == null) {
            throw new ForbiddenAppException(action);
        }
    }

    private void validateEditor(UserContext user, String action) {
        validateAuthenticatedUser(user, action);
        if (!user.hasRole("DEVELOPER") && !user.hasRole("TL") && !user.hasRole("DEVOPS_ADMIN")) {
            throw new ForbiddenAppException(action);
        }
    }

    private void validateScopeAccess(UserContext user, DevelopmentSpec spec, String action) {
        validateRequestScope(user, spec.getApplication(), spec.getSnowGroup(), action);
    }

    private void validateRequestScope(UserContext user, String application, String snowGroup, String action) {
        validateAuthenticatedUser(user, action);
        if (user.isGlobalDevOpsAdmin()) {
            return;
        }
        if (!user.hasScopedAccess(application, snowGroup)) {
            throw new ForbiddenAppException(action);
        }
    }

    private boolean canAccess(UserContext user, DevelopmentSpec spec) {
        if (user == null) {
            return false;
        }
        if (user.isGlobalDevOpsAdmin()) {
            return true;
        }
        return user.hasScopedAccess(spec.getApplication(), spec.getSnowGroup());
    }

    private String normalizeSearchQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return query.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeExportFormat(String format) {
        if (format == null || format.isBlank()) {
            return "markdown";
        }
        return format.trim().toLowerCase(Locale.ROOT);
    }

    private String buildExportFilename(DevelopmentSpec spec, String extension) {
        String base = spec.getTitle() == null || spec.getTitle().isBlank()
                ? "development-spec"
                : spec.getTitle().trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (base.isBlank()) {
            base = "development-spec";
        }
        return base + "." + extension;
    }

    private byte[] buildExportJson(DevelopmentSpec spec) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", spec.getId());
        payload.put("title", spec.getTitle());
        payload.put("moduleName", spec.getModuleName());
        payload.put("programType", spec.getProgramType());
        payload.put("codeStyle", spec.getCodeStyle());
        payload.put("application", spec.getApplication());
        payload.put("snowGroup", spec.getSnowGroup());
        payload.put("status", spec.getStatus().name());
        payload.put("sourcePayload", normalizeSourcePayload(spec.getSourcePayload()));
        payload.put("generatedPayload", normalizeSourcePayload(spec.getGeneratedPayload()));
        payload.put("generatedContent", spec.getGeneratedContent());
        payload.put("generatedAt", spec.getGeneratedAt());
        payload.put("generatedBy", spec.getGeneratedBy());
        payload.put("updatedAt", spec.getUpdatedAt());
        payload.put("updatedBy", spec.getUpdatedBy());
        payload.put("version", spec.getVersion());
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(payload);
        } catch (JsonProcessingException exception) {
            throw new ValidationAppException("Failed to export Development Spec as JSON.");
        }
    }

    private boolean matchesQuery(DevelopmentSpec spec, String normalizedQuery) {
        return containsIgnoreCase(spec.getTitle(), normalizedQuery)
                || containsIgnoreCase(spec.getModuleName(), normalizedQuery)
                || containsIgnoreCase(spec.getApplication(), normalizedQuery)
                || containsIgnoreCase(spec.getSnowGroup(), normalizedQuery);
    }

    private boolean containsIgnoreCase(String value, String normalizedQuery) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    private Map<String, Object> normalizeSourcePayload(Map<String, Object> payload) {
        if (payload == null) {
            return new LinkedHashMap<>();
        }

        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        payload.entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    Object normalizedValue = normalizePayloadValue(entry.getValue());
                    if (normalizedValue != null) {
                        normalized.put(entry.getKey().trim(), normalizedValue);
                    }
                });
        return normalized;
    }

    private Object normalizePayloadValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            String normalized = normalizeBlank(text);
            return normalized;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Map<?, ?> mapValue) {
            LinkedHashMap<String, Object> nested = new LinkedHashMap<>();
            mapValue.entrySet().stream()
                    .filter(entry -> entry.getKey() != null)
                    .sorted((left, right) -> String.valueOf(left.getKey()).compareTo(String.valueOf(right.getKey())))
                    .forEach(entry -> {
                        Object normalizedNested = normalizePayloadValue(entry.getValue());
                        if (normalizedNested != null) {
                            nested.put(String.valueOf(entry.getKey()).trim(), normalizedNested);
                        }
                    });
            return nested.isEmpty() ? null : nested;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> normalizedItems = new ArrayList<>();
            for (Object item : collection) {
                Object normalizedItem = normalizePayloadValue(item);
                if (normalizedItem != null) {
                    normalizedItems.add(normalizedItem);
                }
            }
            return normalizedItems.isEmpty() ? null : List.copyOf(normalizedItems);
        }
        String normalized = normalizeBlank(String.valueOf(value));
        return normalized;
    }

    private boolean hasMeaningfulValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String text) {
            return !text.isBlank();
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().anyMatch(this::hasMeaningfulValue);
        }
        if (value instanceof Map<?, ?> map) {
            return map.values().stream().anyMatch(this::hasMeaningfulValue);
        }
        return true;
    }

    private Map<String, Object> buildGeneratedPayload(DevelopmentSpec spec, Map<String, Object> normalizedSource) {
        LinkedHashMap<String, Object> sections = new LinkedHashMap<>();
        sections.put("title", spec.getTitle());
        sections.put("scope", buildScopeText(spec));
        sections.put("businessObjective", renderValue(normalizedSource.get("businessObjective"), normalizedSource.get("businessObjectives")));
        sections.put("implementationObjective", renderValue(normalizedSource.get("implementationObjective"), normalizedSource.get("implementationObjectives")));
        sections.put("programDetails", buildProgramDetails(spec, normalizedSource));
        sections.put("inputsOutputs", buildInputsOutputs(normalizedSource));
        sections.put("fileModuleNotes", renderValue(normalizedSource.get("fileModuleNotes"), normalizedSource.get("moduleNotes"), spec.getModuleName()));
        sections.put("validationTestNotes", renderValue(normalizedSource.get("validationTestNotes"), normalizedSource.get("testNotes")));
        sections.put("deploymentOperationalNotes", renderValue(normalizedSource.get("deploymentOperationalNotes"), normalizedSource.get("operationalNotes"), normalizedSource.get("deploymentNotes")));
        sections.put("sourcePayload", normalizedSource);
        return sections;
    }

    private String buildGeneratedContent(Map<String, Object> generatedPayload) {
        StringBuilder markdown = new StringBuilder();
        appendSection(markdown, "Title", generatedPayload.get("title"));
        appendSection(markdown, "Scope", generatedPayload.get("scope"));
        appendSection(markdown, "Business Objective", generatedPayload.get("businessObjective"));
        appendSection(markdown, "Implementation Objective", generatedPayload.get("implementationObjective"));
        appendSection(markdown, "Program Details", generatedPayload.get("programDetails"));
        appendSection(markdown, "Inputs / Outputs", generatedPayload.get("inputsOutputs"));
        appendSection(markdown, "File / Module Notes", generatedPayload.get("fileModuleNotes"));
        appendSection(markdown, "Validation / Test Notes", generatedPayload.get("validationTestNotes"));
        appendSection(markdown, "Deployment / Operational Notes", generatedPayload.get("deploymentOperationalNotes"));
        return markdown.toString().trim();
    }

    private void appendSection(StringBuilder markdown, String title, Object value) {
        String rendered = renderValue(value);
        markdown.append("## ").append(title).append("\n\n");
        markdown.append(rendered == null ? "N/A" : rendered).append("\n\n");
    }

    private String buildScopeText(DevelopmentSpec spec) {
        return "Application: " + spec.getApplication() + "\nSNOW Group: " + spec.getSnowGroup();
    }

    private String buildProgramDetails(DevelopmentSpec spec, Map<String, Object> normalizedSource) {
        List<String> lines = new ArrayList<>();
        lines.add("Program Type: " + spec.getProgramType());
        lines.add("Code Style: " + spec.getCodeStyle());
        if (spec.getModuleName() != null) {
            lines.add("Module Name: " + spec.getModuleName());
        }
        String extra = renderValue(normalizedSource.get("programDetails"), normalizedSource.get("programNotes"));
        if (extra != null) {
            lines.add(extra);
        }
        return String.join("\n", lines);
    }

    private String buildInputsOutputs(Map<String, Object> normalizedSource) {
        String combined = renderStructuredListSection(
                "Inputs",
                normalizedSource.get("inputs"),
                "Outputs",
                normalizedSource.get("outputs")
        );
        if (combined != null) {
            return combined;
        }
        return renderValue(normalizedSource.get("inputsOutputs"));
    }

    private String renderStructuredListSection(String firstLabel,
                                               Object firstValue,
                                               String secondLabel,
                                               Object secondValue) {
        List<String> sections = new ArrayList<>();
        String firstRendered = renderValue(firstValue);
        if (firstRendered != null) {
            sections.add(firstLabel + ":\n" + firstRendered);
        }
        String secondRendered = renderValue(secondValue);
        if (secondRendered != null) {
            sections.add(secondLabel + ":\n" + secondRendered);
        }
        return sections.isEmpty() ? null : String.join("\n", sections);
    }

    private String renderValue(Object... candidates) {
        for (Object candidate : candidates) {
            String rendered = renderSingleValue(candidate);
            if (rendered != null) {
                return rendered;
            }
        }
        return null;
    }

    private String renderSingleValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text.isBlank() ? null : text;
        }
        if (value instanceof Collection<?> collection) {
            List<String> renderedItems = collection.stream()
                    .map(this::renderSingleValue)
                    .filter(Objects::nonNull)
                    .toList();
            if (renderedItems.isEmpty()) {
                return null;
            }
            return renderedItems.stream()
                    .map(item -> "- " + item.replace("\n", "\n  "))
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse(null);
        }
        if (value instanceof Map<?, ?> map) {
            List<String> renderedEntries = map.entrySet().stream()
                    .map(entry -> {
                        String nested = renderSingleValue(entry.getValue());
                        if (nested == null) {
                            return null;
                        }
                        return entry.getKey() + ": " + nested;
                    })
                    .filter(Objects::nonNull)
                    .toList();
            return renderedEntries.isEmpty() ? null : String.join("\n", renderedEntries);
        }
        return String.valueOf(value);
    }

    private Map<String, Object> buildAuditContext(DevelopmentSpec spec) {
        return buildAuditContext(spec, null);
    }

    private Map<String, Object> buildAuditContext(DevelopmentSpec spec, String exportFormat) {
        LinkedHashMap<String, Object> context = new LinkedHashMap<>();
        context.put("developmentSpecId", spec.getId());
        context.put("title", spec.getTitle());
        context.put("moduleName", spec.getModuleName());
        context.put("programType", spec.getProgramType());
        context.put("codeStyle", spec.getCodeStyle());
        context.put("status", spec.getStatus().name());
        context.put("application", spec.getApplication());
        context.put("snowGroup", spec.getSnowGroup());
        if (exportFormat != null) {
            context.put("format", exportFormat);
        }
        return context;
    }

    private String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record ExportDocument(String filename, String contentType, byte[] content) {
    }
}
