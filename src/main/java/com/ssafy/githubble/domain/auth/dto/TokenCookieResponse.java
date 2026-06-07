package com.ssafy.githubble.domain.auth.dto;

public record TokenCookieResponse(
        String accessToken,
        String refreshToken
) {
}
