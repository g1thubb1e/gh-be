package com.ssafy.githubble.domain.github.dto.external;

public record GitHubBranchApiResponse(
        String name,
        Commit commit
) {
    public record Commit(
            String sha
    ) {
    }
}
