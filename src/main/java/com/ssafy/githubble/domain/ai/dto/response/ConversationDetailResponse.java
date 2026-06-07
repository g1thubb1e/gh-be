package com.ssafy.githubble.domain.ai.dto.response;

import java.util.List;

public record ConversationDetailResponse(
        ConversationResponse conversation,
        List<MessageResponse> messages
) {
}
