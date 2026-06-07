package com.ssafy.githubble.domain.github.dto.request;

import jakarta.validation.constraints.NotBlank;

public record GitHubRepositorySetupRequest(
        @NotBlank(message = "owner는 필수입니다.")
        String owner,

        @NotBlank(message = "repo는 필수입니다.")
        String repo
) {
}
