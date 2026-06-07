package com.ssafy.githubble.domain.ai.service;

import com.ssafy.githubble.domain.ai.dto.request.ConversationTitleUpdateRequest;
import com.ssafy.githubble.domain.ai.dto.response.ConversationDetailResponse;
import com.ssafy.githubble.domain.ai.dto.response.ConversationResponse;
import com.ssafy.githubble.domain.ai.dto.response.MessageResponse;
import com.ssafy.githubble.domain.ai.entity.AgentConversation;
import com.ssafy.githubble.domain.ai.entity.AgentMessage;
import com.ssafy.githubble.domain.ai.entity.AgentMessageRole;
import com.ssafy.githubble.domain.ai.repository.AgentConversationRepository;
import com.ssafy.githubble.domain.ai.repository.AgentMessageRepository;
import com.ssafy.githubble.domain.auth.domain.User;
import com.ssafy.githubble.domain.auth.repository.UserRepository;
import com.ssafy.githubble.domain.github.domain.GitHubRepositoryEntity;
import com.ssafy.githubble.domain.github.repository.GitHubRepositoryRepository;
import com.ssafy.githubble.global.code.ErrorCode;
import com.ssafy.githubble.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "jpaTransactionManager", readOnly = true)
public class AgentConversationService {

    private static final int AUTO_TITLE_MAX_LENGTH = 40;

    private final AgentConversationRepository conversationRepository;
    private final AgentMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final GitHubRepositoryRepository gitHubRepositoryRepository;

    @Transactional(transactionManager = "jpaTransactionManager")
    public AgentConversation getOrCreateForQuery(Long appuserId, UUID conversationUuid, UUID repoUuid, String question) {
        validateAuthenticated(appuserId);
        if (conversationUuid != null) {
            return getOwnedConversation(appuserId, conversationUuid);
        }
        User appUser = findUser(appuserId);
        GitHubRepositoryEntity repository = findRepositoryByUuid(repoUuid);
        return conversationRepository.save(AgentConversation.create(appUser, repository, makeTitle(question)));
    }

    public Page<ConversationResponse> list(Long appuserId, UUID repoUuid, Pageable pageable) {
        validateAuthenticated(appuserId);
        GitHubRepositoryEntity repository = findRequiredRepositoryByUuid(repoUuid);
        Page<AgentConversation> conversations = conversationRepository
                .findAllByAppUserAppUserIdAndRepositoryRepoIdAndIsDeletedFalse(appuserId, repository.getRepoId(), pageable);
        return toResponses(conversations);
    }

    public ConversationDetailResponse detail(Long appuserId, UUID conversationUuid) {
        AgentConversation conversation = getOwnedConversation(appuserId, conversationUuid);
        List<MessageResponse> messages = messageRepository
                .findAllByConversationConversationIdAndAppuserIdOrderByCreatedAtAsc(conversation.getConversationId(), appuserId)
                .stream()
                .filter(message -> message.getRole() != AgentMessageRole.SYSTEM)
                .map(MessageResponse::from)
                .toList();
        return new ConversationDetailResponse(toResponse(conversation), messages);
    }

    @Transactional(transactionManager = "jpaTransactionManager")
    public ConversationResponse updateTitle(Long appuserId, UUID conversationUuid, ConversationTitleUpdateRequest request) {
        AgentConversation conversation = getOwnedConversation(appuserId, conversationUuid);
        conversation.updateTitle(request.title());
        return toResponse(conversation);
    }

    @Transactional(transactionManager = "jpaTransactionManager")
    public void delete(Long appuserId, UUID conversationUuid) {
        AgentConversation conversation = getOwnedConversation(appuserId, conversationUuid);
        conversation.markDeleted();
    }

    public AgentConversation getOwnedConversation(Long appuserId, UUID conversationUuid) {
        validateAuthenticated(appuserId);
        AgentConversation conversation = conversationRepository.findByConversationUuidAndIsDeletedFalse(conversationUuid)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "채팅방을 찾을 수 없습니다."));
        if (!conversation.getAppuserId().equals(appuserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "채팅방 접근 권한이 없습니다.");
        }
        return conversation;
    }

    @Transactional(transactionManager = "jpaTransactionManager")
    public AgentMessage saveMessage(AgentConversation conversation, AgentMessageRole role, String content, String errorMessage) {
        return messageRepository.save(AgentMessage.create(conversation, role, content, errorMessage));
    }

    private void validateAuthenticated(Long appuserId) {
        if (appuserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "인증이 필요합니다.");
        }
    }

    private User findUser(Long appuserId) {
        return userRepository.findById(appuserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    private ConversationResponse toResponse(AgentConversation conversation) {
        UUID repoUuid = findRepoUuid(conversation.getRepoId());
        return ConversationResponse.from(conversation, repoUuid);
    }

    private Page<ConversationResponse> toResponses(Page<AgentConversation> conversations) {
        Set<Long> repoIds = conversations.stream()
                .map(AgentConversation::getRepoId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, UUID> repoUuidById = repoIds.isEmpty()
                ? Map.of()
                : gitHubRepositoryRepository.findAllById(repoIds).stream()
                .collect(Collectors.toMap(GitHubRepositoryEntity::getRepoId, GitHubRepositoryEntity::getRepoUuid));
        return conversations.map(conversation -> ConversationResponse.from(conversation, repoUuidById.get(conversation.getRepoId())));
    }

    private GitHubRepositoryEntity findRepositoryByUuid(UUID repoUuid) {
        if (repoUuid == null) {
            return null;
        }
        return gitHubRepositoryRepository.findByRepoUuid(repoUuid)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "저장소를 찾을 수 없습니다."));
    }

    private GitHubRepositoryEntity findRequiredRepositoryByUuid(UUID repoUuid) {
        if (repoUuid == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "저장소 UUID는 필수입니다.");
        }
        return findRepositoryByUuid(repoUuid);
    }

    private UUID findRepoUuid(Long repoId) {
        if (repoId == null) {
            return null;
        }
        return gitHubRepositoryRepository.findById(repoId)
                .map(GitHubRepositoryEntity::getRepoUuid)
                .orElse(null);
    }

    private String makeTitle(String question) {
        if (question == null || question.isBlank()) {
            return "새 채팅";
        }
        String normalized = question.trim().replaceAll("\\s+", " ");
        return normalized.length() > AUTO_TITLE_MAX_LENGTH
                ? normalized.substring(0, AUTO_TITLE_MAX_LENGTH)
                : normalized;
    }
}
