package com.wwa.agenthub.contracts.dto;

import java.util.Map;

public record RecordResultRequestDto(Map<String, Object> resultSummary, String resultLogs) {}
