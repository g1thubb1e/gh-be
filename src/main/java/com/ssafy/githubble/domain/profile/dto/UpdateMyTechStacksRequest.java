package com.ssafy.githubble.domain.profile.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record UpdateMyTechStacksRequest(
        @NotNull(message = "techStackUuids는 null일 수 없습니다.")
        List<@NotNull UUID> techStackUuids
) {
}
