package com.ssafy.githubble.domain.github.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record GitDiagramStreamEvent(
        String status,
        @JsonProperty("session_id") String sessionId,
        Map<String, Object> data,
        String error,
        @JsonProperty("error_code") String errorCode,
        @JsonProperty("failure_stage") String failureStage,
        @JsonProperty("validation_error") String validationError,
        @JsonProperty("cost_summary") Map<String, Object> costSummary,
        @JsonProperty("latest_session_audit") Map<String, Object> latestSessionAudit
) {
}
