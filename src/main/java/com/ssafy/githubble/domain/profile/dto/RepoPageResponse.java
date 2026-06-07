package com.ssafy.githubble.domain.profile.dto;

import java.util.List;

public record RepoPageResponse(
        int page,
        int size,
        int totalPages,
        long totalElements,
        List<RepoResponse> content
) {}

