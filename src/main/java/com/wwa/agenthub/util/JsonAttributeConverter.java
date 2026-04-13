package com.wwa.agenthub.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Map;

/**
 * JPA AttributeConverter that serializes Map&lt;String, Object&gt; to/from a JSON string.
 *
 * <p>Columns using this converter should be declared with {@code columnDefinition = "CLOB"}
 * for Oracle production deployments to accommodate large payloads.
 * H2 in tests handles CLOB storage correctly for MVP-scale data.
 *
 * <p>Usage on an entity field:
 * <pre>
 *   {@literal @}Column(name = "input_parameters", columnDefinition = "CLOB")
 *   {@literal @}Convert(converter = JsonAttributeConverter.class)
 *   private Map&lt;String, Object&gt; inputParameters;
 * </pre>
 */
@Converter
public class JsonAttributeConverter implements AttributeConverter<Map<String, Object>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize map to JSON", e);
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(dbData, MAP_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize JSON to map", e);
        }
    }
}
