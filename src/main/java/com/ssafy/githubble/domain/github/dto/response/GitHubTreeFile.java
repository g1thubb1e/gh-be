package com.ssafy.githubble.domain.github.dto.response;

public record GitHubTreeFile(
        String path,
        String type,
        Long size,
        String extension,
        String sha
) {
}
