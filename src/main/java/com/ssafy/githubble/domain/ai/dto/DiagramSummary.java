package com.ssafy.githubble.domain.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DiagramSummary {
    private final String archId;
    private final String username;
    private final String repo;
    private final String lastAttempt;
    private final Long ingestedAt;
}
