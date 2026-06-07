package com.ssafy.githubble.domain.github.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GitHubRepositoryApiResponse(
        @JsonProperty("id") Long githubRepoId,
        String name,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("html_url") String htmlUrl,
        String description,
        @JsonProperty("default_branch") String defaultBranch,
        String language,
        @JsonProperty("private") boolean privateRepository,
        @JsonProperty("stargazers_count") long stargazersCount,
        @JsonProperty("forks_count") long forksCount,
        @JsonProperty("open_issues_count") long openIssuesCount
) {
}
