package com.ssafy.githubble.domain.profile.dto;


import java.time.OffsetDateTime;

// /api/v1/profiles/me/repositories 프론트 응답 DTO
public record RepoResponse(
        String username,
        String repoName,
        String description,
        String htmlUrl,
        String language,
        int stargazerCnt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
