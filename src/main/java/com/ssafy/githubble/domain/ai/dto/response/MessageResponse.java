package com.ssafy.githubble.domain.ai.dto.response;

import com.ssafy.githubble.domain.ai.entity.AgentMessage;
import com.ssafy.githubble.domain.ai.entity.AgentMessageRole;

import java.time.LocalDateTime;
import java.util.UUID;

public record MessageResponse(
        Long messageId,
        Long appuserId,
        UUID messageUuid,
        UUID conversationUuid,
        AgentMessageRole role,
        String content,
        String errorMessage,
        LocalDateTime createdAt
) {
    public static MessageResponse from(AgentMessage message) {
        return new MessageResponse(
                message.getMessageId(),
                message.getAppuserId(),
                message.getMessageUuid(),
                message.getConversation().getConversationUuid(),
                message.getRole(),
                message.getContent(),
                message.getErrorMessage(),
                message.getCreatedAt()
        );
    }
}
