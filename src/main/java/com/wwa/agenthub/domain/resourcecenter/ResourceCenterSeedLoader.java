package com.wwa.agenthub.domain.resourcecenter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwa.agenthub.domain.resourcecenter.model.DirectoryScope;
import com.wwa.agenthub.errors.ValidationAppException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Loads and validates the packaged Resource Center seed catalog. */
@Component
@RequiredArgsConstructor
public class ResourceCenterSeedLoader {

    private static final String SEED_RESOURCE = "resource-center/seed-catalog.json";

    private final ResourceCenterValidator validator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<DirectoryScope> loadAndValidate() {
        List<DirectoryScope> scopes = load();
        validator.validateFullCatalog(scopes);
        return scopes;
    }

    private List<DirectoryScope> load() {
        ClassPathResource resource = new ClassPathResource(SEED_RESOURCE);
        if (!resource.exists()) {
            throw new ValidationAppException("catalog: seed resource not found: " + SEED_RESOURCE);
        }
        try (InputStream input = resource.getInputStream()) {
            return objectMapper.readValue(input, new TypeReference<>() {});
        } catch (IOException ex) {
            throw new ValidationAppException("catalog: failed to parse seed catalog: " + ex.getMessage());
        }
    }
}
