package com.ssafy.githubble.domain.profile.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

// GitHub Search API(/search/repositories) 응답 래퍼 DTO
@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubRepoSearchResponse(
        List<GithubRepoResponse> items
) {
}
