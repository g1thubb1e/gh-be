package com.ssafy.githubble.domain.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.githubble.domain.ai.component.GraphRagAssistant;
import com.ssafy.githubble.domain.ai.component.RagJudgeAssistant;
import com.ssafy.githubble.domain.ai.component.RuleBasedQueryClassifier;
import com.ssafy.githubble.domain.ai.component.SourceAnalysisAssistant;
import dev.langchain4j.model.chat.ChatModel;
import com.ssafy.githubble.domain.ai.component.retrieve.GraphRetriever;
import com.ssafy.githubble.domain.ai.component.retrieve.HybridRetriever;
import com.ssafy.githubble.domain.ai.component.retrieve.VectorRetriever;
import com.ssafy.githubble.domain.ai.dto.request.QueryRequest;
import com.ssafy.githubble.domain.ai.entity.AgentConversation;
import com.ssafy.githubble.domain.ai.repository.QaEvalRepository;
import com.ssafy.githubble.domain.auth.domain.User;
import com.ssafy.githubble.domain.github.domain.GitHubRepositoryEntity;
import com.ssafy.githubble.domain.github.repository.GitHubRepositoryRepository;
import com.ssafy.githubble.domain.github.service.GitHubRepositoryParseService;
import com.ssafy.githubble.neo4j.repository.ArchitectureRepository;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueryServiceTest {

    private static final Long APPUSER_ID = 1L;
    private static final Long REPO_ID = 20L;
    private static final UUID REPO_UUID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private GitHubRepositoryRepository gitHubRepositoryRepository;
    private AgentConversationService conversationService;
    private QueryService queryService;

    @BeforeEach
    void setUp() {
        gitHubRepositoryRepository = mock(GitHubRepositoryRepository.class);
        conversationService = mock(AgentConversationService.class);
        queryService = new QueryService(
                mock(RuleBasedQueryClassifier.class),
                mock(RoutingService.class),
                mock(GraphRetriever.class),
                mock(VectorRetriever.class),
                mock(HybridRetriever.class),
                mock(ChatModel.class),
                mock(StreamingChatModel.class),
                mock(RagJudgeAssistant.class),
                mock(GraphRagAssistant.class),
                mock(QaEvalRepository.class),
                mock(ArchitectureRepository.class),
                gitHubRepositoryRepository,
                new ObjectMapper(),
                conversationService,
                mock(GitHubRepositoryParseService.class),
                mock(SourceAnalysisAssistant.class)
        );
    }

    @Test
    @DisplayName("새 채팅 요청의 repoUuid와 archId가 다른 저장소를 가리키면 예외가 발생한다")
    void answerWithConversationRejectsMismatchedRepoUuidAndArchId() {
        QueryRequest request = queryRequest(REPO_UUID, "other-owner__other-repo");
        GitHubRepositoryEntity repository = repository();
        when(gitHubRepositoryRepository.findByRepoUuid(REPO_UUID)).thenReturn(Optional.of(repository));

        assertThatThrownBy(() -> queryService.answerWithConversation(APPUSER_ID, null, request, false))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(throwable -> {
                    ResponseStatusException exception = (ResponseStatusException) throwable;
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason()).contains("ARCHID_REPO_MISMATCH");
                });
        verify(conversationService, never()).getOrCreateForQuery(any(), any(), any(), any());
    }

    @Test
    @DisplayName("기존 채팅방의 저장소와 요청 archId가 다른 저장소를 가리키면 예외가 발생한다")
    void answerWithConversationRejectsMismatchedConversationRepoAndArchId() {
        QueryRequest request = queryRequest(null, "other-owner__other-repo");
        GitHubRepositoryEntity repository = repository();
        AgentConversation conversation = AgentConversation.create(user(), repository, "기존 채팅");
        when(conversationService.getOrCreateForQuery(APPUSER_ID, REPO_UUID, null, request.getQuestion()))
                .thenReturn(conversation);
        when(gitHubRepositoryRepository.findById(REPO_ID)).thenReturn(Optional.of(repository));

        assertThatThrownBy(() -> queryService.answerWithConversation(APPUSER_ID, REPO_UUID, request, false))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(throwable -> {
                    ResponseStatusException exception = (ResponseStatusException) throwable;
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason()).contains("ARCHID_REPO_MISMATCH");
                });
        verify(conversationService, never()).saveMessage(any(), any(), any(), any());
    }

    private QueryRequest queryRequest(UUID repoUuid, String archId) {
        QueryRequest request = new QueryRequest();
        request.setQuestion("이 저장소의 인증 흐름은 어디서 시작하나요?");
        request.setRepoUuid(repoUuid);
        request.setArchId(archId);
        return request;
    }

    private GitHubRepositoryEntity repository() {
        GitHubRepositoryEntity repository = mock(GitHubRepositoryEntity.class);
        when(repository.getRepoId()).thenReturn(REPO_ID);
        when(repository.getRepoUuid()).thenReturn(REPO_UUID);
        when(repository.getOwnerLogin()).thenReturn("spring-projects");
        when(repository.getRepoName()).thenReturn("spring-petclinic");
        return repository;
    }

    private User user() {
        User user = mock(User.class);
        when(user.getAppUserId()).thenReturn(APPUSER_ID);
        return user;
    }
}
