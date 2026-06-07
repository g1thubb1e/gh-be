package com.ssafy.githubble.domain.profile.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

// GitHub user 응답 DTO
public record GithubUserApiResponse(
        String login, // username
        Long id,
        String name,
        @JsonProperty("avatar_url")
        String avatarUrl, // github_avatar_url
        String type,
        String company,
        String blog,
        String location,
        String email,
        String bio,
        @JsonProperty("public_repos")
        Integer publicRepos, // public_repos_count
        @JsonProperty("created_at")
        LocalDateTime createdAt
) {
}
