package com.ssafy.githubble.domain.auth.dto;

public record GithubEmailResponse(
        String email,
        boolean primary,
        boolean verified,
        String visibility
) {
}
