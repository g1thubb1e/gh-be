package com.ssafy.githubble.domain.github.dto.response;

import java.time.OffsetDateTime;

public record GitDiagramResponse(
        int version,
        String username,
        String repo,
        String commitSha,
        String diagram,
        OffsetDateTime generatedAt
) {
}
