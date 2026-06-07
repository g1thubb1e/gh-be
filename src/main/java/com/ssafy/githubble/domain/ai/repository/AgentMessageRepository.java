package com.ssafy.githubble.domain.ai.repository;

import com.ssafy.githubble.domain.ai.entity.AgentMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentMessageRepository extends JpaRepository<AgentMessage, Long> {
    List<AgentMessage> findAllByConversationConversationIdAndAppuserIdOrderByCreatedAtAsc(Long conversationId, Long appuserId);
}
