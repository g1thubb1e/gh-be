package com.ssafy.githubble.domain.profile.dto;

import java.util.List;

// /api/v1/profiles/me/repositories 응답 DTO
public record MyRepoListResponse(
        int totalCount,
        List<RepoResponse> repositories
) {}
