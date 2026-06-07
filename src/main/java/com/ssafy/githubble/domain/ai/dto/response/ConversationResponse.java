package com.ssafy.githubble.domain.ai.dto.response;

import com.ssafy.githubble.domain.ai.entity.AgentConversation;

import java.time.LocalDateTime;
import java.util.UUID;

public record ConversationResponse(
        Long appuserId,
        UUID conversationUuid,
        Long repoId,
        UUID repoUuid,
        String title,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ConversationResponse from(AgentConversation conversation) {
        return from(conversation, null);
    }

    public static ConversationResponse from(AgentConversation conversation, UUID repoUuid) {
        return new ConversationResponse(
                conversation.getAppuserId(),
                conversation.getConversationUuid(),
                conversation.getRepoId(),
                repoUuid,
                conversation.getTitle(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt()
        );
    }
}
