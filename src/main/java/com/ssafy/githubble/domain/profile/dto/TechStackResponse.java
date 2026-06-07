package com.ssafy.githubble.domain.profile.dto;

import com.ssafy.githubble.domain.profile.domain.TechStack;
import java.util.UUID;

public record TechStackResponse(
        UUID techStackUuid,
        String name,
        String iconUrl,
        String color
) {
    public static TechStackResponse from(TechStack techStack, String iconUrl) {
        return new TechStackResponse(
                techStack.getTechstackUuid(),
                techStack.getName(),
                iconUrl,
                techStack.getColor()
        );
    }
}
