package com.ssafy.githubble.domain.github.dto.response;

public record GitHubRepoSummaryResponse(
        String owner,
        String repo,
        String explanation
) {
}
