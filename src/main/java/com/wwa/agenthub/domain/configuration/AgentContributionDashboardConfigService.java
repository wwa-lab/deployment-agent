package com.wwa.agenthub.domain.configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.dto.AgentContributionDashboardStatusDto;
import com.wwa.agenthub.contracts.enums.ConfigKey;
import com.wwa.agenthub.errors.ValidationAppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AgentContributionDashboardConfigService {

    private static final ConfigKey STATUS_CONFIG_KEY = ConfigKey.agent_contribution_dashboard_statuses;
    private static final String DESCRIPTION = "Agent Contribute Dashboard stage status overrides";
    private static final Set<String> ALLOWED_STAGE_KEYS = Set.of(
            "planning",
            "estimation",
            "discovery",
            "build",
            "testing",
            "deployment",
            "maintenance"
    );
    private static final Set<String> ALLOWED_STATUS_VALUES = Set.of(
            "implemented",
            "in-progress",
            "backlog",
            "not-implemented"
    );

    private final ConfigurationService configurationService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public AgentContributionDashboardStatusDto getStatuses() {
        return configurationService.getByKey(STATUS_CONFIG_KEY)
                .map(item -> new AgentContributionDashboardStatusDto(
                        parseStatuses(item.getConfigValue()),
                        item.getUpdatedBy(),
                        item.getUpdatedAt()
                ))
                .orElseGet(() -> new AgentContributionDashboardStatusDto(Map.of(), null, null));
    }

    @Transactional
    public AgentContributionDashboardStatusDto updateStatuses(
            AgentContributionDashboardStatusDto.UpsertRequest request,
            UserContext user
    ) {
        Map<String, String> normalizedStatuses = normalizeStatuses(request.statuses());
        String payload = serializeStatuses(normalizedStatuses);
        configurationService.upsert(STATUS_CONFIG_KEY, payload, DESCRIPTION, user);
        return getStatuses();
    }

    private Map<String, String> parseStatuses(String payload) {
        try {
            Map<String, String> parsed = objectMapper.readValue(payload, new TypeReference<>() {});
            return normalizeStatuses(parsed);
        } catch (JsonProcessingException e) {
            throw new ValidationAppException("Stored Agent Contribute Dashboard status configuration is invalid");
        }
    }

    private Map<String, String> normalizeStatuses(Map<String, String> statuses) {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (statuses == null) {
            return normalized;
        }

        for (String stageKey : ALLOWED_STAGE_KEYS) {
            String status = trimToNull(statuses.get(stageKey));
            if (status == null) {
                continue;
            }
            if (!ALLOWED_STATUS_VALUES.contains(status)) {
                throw new ValidationAppException("Unsupported Agent Contribute Dashboard status: '" + status + "'");
            }
            normalized.put(stageKey, status);
        }

        for (String stageKey : statuses.keySet()) {
            if (!ALLOWED_STAGE_KEYS.contains(stageKey)) {
                throw new ValidationAppException("Unknown Agent Contribute Dashboard stage: '" + stageKey + "'");
            }
        }

        return normalized;
    }

    private String serializeStatuses(Map<String, String> statuses) {
        try {
            return objectMapper.writeValueAsString(statuses);
        } catch (JsonProcessingException e) {
            throw new ValidationAppException("Failed to serialize Agent Contribute Dashboard status configuration");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
