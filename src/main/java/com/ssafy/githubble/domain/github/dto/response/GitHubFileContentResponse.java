package com.ssafy.githubble.domain.github.dto.response;

public record GitHubFileContentResponse(
        String owner,
        String repo,
        String ref,
        String type,
        String path,
        String name,
        String sha,
        Long size,
        String encoding,
        String content,
        boolean contentAvailable,
        boolean binary,
        String unavailableReason,
        String htmlUrl,
        boolean cached
) {
}
