package com.wwa.agenthub.contracts.dto.integration;

import com.wwa.agenthub.contracts.enums.CapabilityType;
import com.wwa.agenthub.contracts.enums.IntegrationClientType;

import java.time.LocalDate;
import java.util.List;

public record CapabilityUsageDto(
        Filters filters,
        Totals totals,
        List<Row> items
) {
    public CapabilityUsageDto {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public record Filters(
            String capabilityId,
            String skillId,
            String team,
            String projectId,
            String agent,
            LocalDate from,
            LocalDate to,
            IntegrationClientType clientType
    ) {
    }

    public record Totals(long invocationCount, long distinctCapabilityCount) {
    }

    public record Row(
            CapabilityType capabilityType,
            String capabilityId,
            String skillId,
            long invocationCount,
            long successCount,
            long failureCount,
            long cancelledCount,
            long runningCount,
            double successRate,
            double failureRate,
            double averageDurationMs,
            long userCount,
            List<Version> versionDistribution
    ) {
        public Row {
            versionDistribution = versionDistribution == null
                    ? List.of()
                    : List.copyOf(versionDistribution);
        }
    }

    public record Version(String version, long count, double percentage) {
    }
}
