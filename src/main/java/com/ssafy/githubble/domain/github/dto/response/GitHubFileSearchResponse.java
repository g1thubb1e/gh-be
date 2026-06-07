package com.ssafy.githubble.domain.github.dto.response;

import java.util.List;

public record GitHubFileSearchResponse(
        String owner,
        String repo,
        String ref,
        String query,
        List<GitHubTreeFile> matches,
        GitHubFileContentResponse singleMatchContent
) {
}
