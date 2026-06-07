package com.ssafy.githubble.domain.github.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record GitHubRepositoryParseRequest(
        @NotBlank(message = "owner는 필수입니다.")
        @Schema(description = "필수. GitHub 저장소 owner 또는 organization 이름입니다.", example = "spring-projects", requiredMode = Schema.RequiredMode.REQUIRED)
        String owner,

        @NotBlank(message = "repo는 필수입니다.")
        @Schema(description = "필수. GitHub 저장소 이름입니다.", example = "spring-petclinic", requiredMode = Schema.RequiredMode.REQUIRED)
        String repo
) {
}
