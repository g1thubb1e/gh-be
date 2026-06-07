package com.ssafy.githubble.domain.ai.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentConversationServiceTest {

    private static final Long APPUSER_ID = 1L;
    private static final Long REPO_ID = 20L;
    private static final UUID REPO_UUID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CONVERSATION_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private AgentConversationRepository conversationRepository;
    private AgentMessageRepository messageRepository;
    private UserRepository userRepository;
    private GitHubRepositoryRepository gitHubRepositoryRepository;
    private AgentConversationService service;

    @BeforeEach
    void setUp() {
        conversationRepository = mock(AgentConversationRepository.class);
        messageRepository = mock(AgentMessageRepository.class);
        userRepository = mock(UserRepository.class);
        gitHubRepositoryRepository = mock(GitHubRepositoryRepository.class);
        service = new AgentConversationService(conversationRepository, messageRepository, userRepository, gitHubRepositoryRepository);
    }

    @Test
    @DisplayName("Query 요청에 conversationUuid가 없으면 repoUuid로 저장소를 찾아 새 채팅방을 생성한다")
    void getOrCreateForQueryCreatesNewConversationWhenConversationUuidIsNull() {
        when(conversationRepository.save(any(AgentConversation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        User user = user(APPUSER_ID);
        when(userRepository.findById(APPUSER_ID)).thenReturn(Optional.of(user));
        GitHubRepositoryEntity repository = repository();
        when(gitHubRepositoryRepository.findByRepoUuid(REPO_UUID))
                .thenReturn(Optional.of(repository));

        AgentConversation conversation = service.getOrCreateForQuery(APPUSER_ID, null, REPO_UUID,
                "  첫   질문으로   제목을   만듭니다  ");

        ArgumentCaptor<AgentConversation> captor = ArgumentCaptor.forClass(AgentConversation.class);
        verify(conversationRepository).save(captor.capture());
        assertThat(conversation).isSameAs(captor.getValue());
        assertThat(captor.getValue().getAppuserId()).isEqualTo(APPUSER_ID);
        assertThat(captor.getValue().getRepoId()).isEqualTo(20L);
        assertThat(captor.getValue().getTitle()).isEqualTo("첫 질문으로 제목을 만듭니다");
    }

    @Test
    @DisplayName("Query 요청에 conversationUuid가 있으면 기존 채팅방을 사용한다")
    void getOrCreateForQueryUsesExistingConversationWhenConversationUuidExists() {
        AgentConversation existing = AgentConversation.create(user(APPUSER_ID), repository(), "기존 채팅");
        when(conversationRepository.findByConversationUuidAndIsDeletedFalse(CONVERSATION_UUID))
                .thenReturn(Optional.of(existing));

        AgentConversation conversation = service.getOrCreateForQuery(APPUSER_ID, CONVERSATION_UUID, REPO_UUID, "새 질문");

        assertThat(conversation).isSameAs(existing);
        verify(conversationRepository).findByConversationUuidAndIsDeletedFalse(CONVERSATION_UUID);
        verify(conversationRepository, never()).save(any(AgentConversation.class));
    }

    @Test
    @DisplayName("채팅방이 존재하지 않으면 NOT_FOUND 예외가 발생한다")
    void getOwnedConversationThrowsNotFoundWhenConversationDoesNotExist() {
        when(conversationRepository.findByConversationUuidAndIsDeletedFalse(CONVERSATION_UUID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOwnedConversation(APPUSER_ID, CONVERSATION_UUID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("다른 사용자의 채팅방에 접근하면 FORBIDDEN 예외가 발생한다")
    void getOwnedConversationRejectsOtherUsersConversation() {
        AgentConversation conversation = AgentConversation.create(user(2L), null, "다른 사용자 채팅");
        when(conversationRepository.findByConversationUuidAndIsDeletedFalse(CONVERSATION_UUID))
                .thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> service.getOwnedConversation(APPUSER_ID, CONVERSATION_UUID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("채팅방 삭제 시 soft delete 처리한다")
    void deleteMarksConversationAsDeleted() {
        AgentConversation conversation = AgentConversation.create(user(APPUSER_ID), null, "삭제할 채팅");
        when(conversationRepository.findByConversationUuidAndIsDeletedFalse(CONVERSATION_UUID))
                .thenReturn(Optional.of(conversation));

        service.delete(APPUSER_ID, CONVERSATION_UUID);

        assertThat(conversation.getIsDeleted()).isTrue();
    }

    @Test
    @DisplayName("메시지 저장 시 role, content, errorMessage를 함께 저장한다")
    void saveMessageStoresRoleContentAndErrorMessageForDebugging() {
        AgentConversation conversation = AgentConversation.create(user(APPUSER_ID), null, "질문");
        when(messageRepository.save(any(AgentMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AgentMessage message = service.saveMessage(conversation, AgentMessageRole.ASSISTANT,
                "답변 생성 중 오류가 발생했습니다.", "timeout");

        ArgumentCaptor<AgentMessage> captor = ArgumentCaptor.forClass(AgentMessage.class);
        verify(messageRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(AgentMessageRole.ASSISTANT);
        assertThat(captor.getValue().getContent()).isEqualTo("답변 생성 중 오류가 발생했습니다.");
        assertThat(captor.getValue().getErrorMessage()).isEqualTo("timeout");
        assertThat(message.getConversation()).isEqualTo(conversation);
    }

    @Test
    @DisplayName("채팅방 목록 조회 시 repoUuid가 없으면 INVALID_INPUT 예외가 발생한다")
    void listMineOnlyRejectsMissingRepoUuid() {
        Pageable pageable = PageRequest.of(0, 20);

        assertThatThrownBy(() -> service.list(APPUSER_ID, null, pageable))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(conversationRepository, never()).findAllByAppUserAppUserIdAndRepositoryRepoIdAndIsDeletedFalse(any(), any(), any());
    }

    @Test
    @DisplayName("채팅방 목록 조회 시 repoUuid가 있으면 인증 사용자의 특정 저장소 채팅방만 조회한다")
    void listMineOnlyQueriesAuthenticatedUserConversationsInRepository() {
        Pageable pageable = PageRequest.of(0, 20);
        GitHubRepositoryEntity repository = repository();
        when(gitHubRepositoryRepository.findByRepoUuid(REPO_UUID))
                .thenReturn(Optional.of(repository));
        when(conversationRepository.findAllByAppUserAppUserIdAndRepositoryRepoIdAndIsDeletedFalse(APPUSER_ID, REPO_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.list(APPUSER_ID, REPO_UUID, pageable);

        verify(conversationRepository).findAllByAppUserAppUserIdAndRepositoryRepoIdAndIsDeletedFalse(APPUSER_ID, REPO_ID, pageable);
    }

    private User user(Long appuserId) {
        User user = mock(User.class);
        when(user.getAppUserId()).thenReturn(appuserId);
        return user;
    }

    private GitHubRepositoryEntity repository() {
        GitHubRepositoryEntity repository = mock(GitHubRepositoryEntity.class);
        when(repository.getRepoId()).thenReturn(REPO_ID);
        when(repository.getRepoUuid()).thenReturn(REPO_UUID);
        return repository;
    }
}
