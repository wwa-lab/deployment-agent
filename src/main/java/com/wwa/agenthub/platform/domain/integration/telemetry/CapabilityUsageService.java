package com.wwa.agenthub.platform.domain.integration.telemetry;

import com.wwa.agenthub.contracts.dto.integration.CapabilityUsageDto;
import com.wwa.agenthub.contracts.enums.CapabilityType;
import com.wwa.agenthub.platform.domain.integration.IntegrationApiException;
import com.wwa.agenthub.platform.domain.integration.auth.IntegrationActor;
import com.wwa.agenthub.platform.domain.integration.auth.IntegrationAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CapabilityUsageService {

    private final CapabilityUsageQueryRepository queryRepository;
    private final IntegrationAuthorizationService authorizationService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public CapabilityUsageDto aggregate(
            CapabilityUsageDto.Filters requested,
            IntegrationActor actor
    ) {
        authorizationService.assertCanViewTelemetry(actor);
        CapabilityUsageDto.Filters filters = normalize(requested);
        CapabilityUsageQueryRepository.Facts facts = queryRepository.aggregate(
                filters,
                actor,
                filters.from().atStartOfDay(ZoneOffset.UTC).toInstant(),
                filters.to().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());

        Map<Key, List<CapabilityUsageQueryRepository.VersionCount>> versions = facts.versions().stream()
                .collect(Collectors.groupingBy(
                        value -> new Key(value.capabilityType(), value.capabilityId())));
        List<CapabilityUsageDto.Row> rows = facts.aggregates().stream()
                .map(value -> row(value, versions.getOrDefault(
                        new Key(value.capabilityType(), value.capabilityId()), List.of())))
                .toList();
        long invocationCount = rows.stream().mapToLong(CapabilityUsageDto.Row::invocationCount).sum();
        return new CapabilityUsageDto(
                filters,
                new CapabilityUsageDto.Totals(invocationCount, rows.size()),
                rows);
    }

    private CapabilityUsageDto.Filters normalize(CapabilityUsageDto.Filters filters) {
        CapabilityUsageDto.Filters source = filters == null
                ? new CapabilityUsageDto.Filters(null, null, null, null, null, null, null, null)
                : filters;
        if (notBlank(source.skillId())
                && notBlank(source.capabilityId())
                && !source.skillId().equals(source.capabilityId())) {
            throw IntegrationApiException.badRequest(
                    "INVALID_REQUEST",
                    "skillId and capabilityId filters conflict.");
        }
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        LocalDate from = source.from() == null ? today.minusDays(89) : source.from();
        LocalDate to = source.to() == null ? today : source.to();
        if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) > 366) {
            throw IntegrationApiException.badRequest(
                    "INVALID_REQUEST",
                    "Telemetry date range is invalid or exceeds 366 days.");
        }
        return new CapabilityUsageDto.Filters(
                normalize(source.capabilityId()),
                normalize(source.skillId()),
                normalize(source.team()),
                normalize(source.projectId()),
                normalize(source.agent()),
                from,
                to,
                source.clientType());
    }

    private static CapabilityUsageDto.Row row(
            CapabilityUsageQueryRepository.Aggregate value,
            List<CapabilityUsageQueryRepository.VersionCount> versions
    ) {
        long decided = value.successCount() + value.failureCount();
        List<CapabilityUsageDto.Version> distribution = versions.stream()
                .map(version -> new CapabilityUsageDto.Version(
                        version.version(),
                        version.count(),
                        percentage(version.count(), value.invocationCount())))
                .toList();
        return new CapabilityUsageDto.Row(
                value.capabilityType(),
                value.capabilityId(),
                value.capabilityType() == CapabilityType.SKILL ? value.capabilityId() : null,
                value.invocationCount(),
                value.successCount(),
                value.failureCount(),
                value.cancelledCount(),
                value.runningCount(),
                percentage(value.successCount(), decided),
                percentage(value.failureCount(), decided),
                value.averageDurationMs(),
                value.userCount(),
                distribution);
    }

    private static double percentage(long numerator, long denominator) {
        return denominator == 0 ? 0 : Math.round((numerator * 10000.0) / denominator) / 100.0;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private record Key(CapabilityType type, String id) {
    }
}
