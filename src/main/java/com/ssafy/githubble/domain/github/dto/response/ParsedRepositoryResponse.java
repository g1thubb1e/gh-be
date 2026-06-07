package com.ssafy.githubble.domain.github.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ParsedRepositoryResponse(
        UUID repoUuid,
        String owner,
        String repo,
        String fullName,
        String htmlUrl,
        String description,
        String defaultBranch,
        String primaryLanguage,
        long stars,
        long forks,
        long openIssueCount,
        long openPullRequestCount,
        long openIssueAndPullRequestCount,
        Map<String, Long> languages,
        boolean treeTruncated,
        List<GitHubTreeFile> treeFiles,
        GitHubReadmeInfo readme,
        List<GitHubConfigFile> configFiles,
        List<String> warnings,
        LocalDateTime parsedAt
) {
}
