package com.ssafy.githubble.domain.github.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GitHubContentApiResponse(
        String name,
        String path,
        String sha,
        Long size,
        String type,
        String encoding,
        String content,
        @JsonProperty("html_url") String htmlUrl
) {
}
