package com.ssafy.githubble.domain.ai.repository;

import com.ssafy.githubble.domain.ai.entity.AgentConversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AgentConversationRepository extends JpaRepository<AgentConversation, Long> {
    Optional<AgentConversation> findByConversationUuidAndIsDeletedFalse(UUID conversationUuid);
    Page<AgentConversation> findAllByAppUserAppUserIdAndRepositoryRepoIdAndIsDeletedFalse(Long appuserId, Long repoId, Pageable pageable);
}
