package com.ssafy.githubble.domain.github.dto.response;

import com.ssafy.githubble.domain.profile.domain.TechStack;

import java.util.UUID;

public record GithubRepoTechStackResponse(
        UUID techStackUuid,
        String name,
        String iconUrl,
        String color
) {
    public static GithubRepoTechStackResponse from(TechStack techStack, String iconUrl) {
        return new GithubRepoTechStackResponse(
                techStack.getTechstackUuid(),
                techStack.getName(),
                iconUrl,
                techStack.getColor()
        );
    }
}
