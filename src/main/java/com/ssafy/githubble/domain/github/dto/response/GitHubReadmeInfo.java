package com.ssafy.githubble.domain.github.dto.response;

public record GitHubReadmeInfo(
        String path,
        String content,
        String encoding
) {
}
