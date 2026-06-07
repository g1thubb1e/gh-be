package com.ssafy.githubble.domain.ai.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConversationTitleUpdateRequest(
        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 200, message = "제목은 200자 이하여야 합니다.")
        @Schema(description = "필수. 수정할 채팅방 제목입니다. 최대 200자입니다.", example = "인증 흐름 분석", requiredMode = Schema.RequiredMode.REQUIRED)
        String title
) {
}
