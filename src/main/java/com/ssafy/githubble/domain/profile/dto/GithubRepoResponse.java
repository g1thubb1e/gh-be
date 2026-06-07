package com.ssafy.githubble.domain.profile.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

// GitHub /user/repos 응답 1개(repo) DTO
@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubRepoResponse(
        @JsonProperty("id")
        Long githubRepoId, // github_repo_id

        String name, // repo_name

        String description,

        @JsonProperty("html_url")
        String htmlUrl,

        String language,

        @JsonProperty("stargazers_count")
        Integer stargazersCount,

        @JsonProperty("pushed_at")
        OffsetDateTime pushedAt,

        @JsonProperty("created_at")
        OffsetDateTime createdAt,

        @JsonProperty("updated_at")
        OffsetDateTime updatedAt,

        Owner owner
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Owner(
            String login
    ) {}
}
