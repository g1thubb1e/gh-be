package com.ssafy.githubble.domain.profile.dto;

import com.ssafy.githubble.domain.profile.domain.GithubProfile;

import java.time.LocalDateTime;

public record ProfileResponse(
        String username,
        String name,
        String githubAvatarUrl,
        String type,
        String company,
        String blog,
        String location,
        String email,
        String bio,
        Integer publicReposCount,
        LocalDateTime createdAt,
        Integer thisYearContributions
) {

    // 서비스 사용자 조회
    public static ProfileResponse from(GithubProfile profile) {
        return new ProfileResponse(
                profile.getUsername(),
                profile.getName(),
                profile.getGithubAvatarUrl(),
                profile.getType(),
                profile.getCompany(),
                profile.getBlog(),
                profile.getLocation(),
                profile.getEmail(),
                profile.getBio(),
                profile.getPublicReposCount(),
                profile.getCreatedAt(),
                profile.getThisYearContributions()
        );
    }

    // 외부 사용자 조회
    public static ProfileResponse from(GithubUserApiResponse githubUser) {
        return from(githubUser, null);
    }

    public static ProfileResponse from(
            GithubUserApiResponse githubUser,
            Integer thisYearContributions
    ) {
        return new ProfileResponse(
                githubUser.login(),
                githubUser.name(),
                githubUser.avatarUrl(),
                githubUser.type(),
                githubUser.company(),
                githubUser.blog(),
                githubUser.location(),
                githubUser.email(),
                githubUser.bio(),
                githubUser.publicRepos(),
                githubUser.createdAt(),
                thisYearContributions
        );
    }
}
