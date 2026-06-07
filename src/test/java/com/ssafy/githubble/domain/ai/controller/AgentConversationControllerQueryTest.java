package com.ssafy.githubble.domain.ai.controller;

import com.ssafy.githubble.domain.ai.dto.request.QueryRequest;
import com.ssafy.githubble.domain.ai.dto.response.QueryResponse;
import com.ssafy.githubble.domain.ai.service.AgentConversationService;
import com.ssafy.githubble.domain.ai.service.QueryService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentConversationControllerQueryTest {

    private static final Long APPUSER_ID = 1L;
    private static final UUID CONVERSATION_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private QueryService queryService;
    private AgentConversationController agentConversationController;
    private HttpServletRequest httpServletRequest;

    @BeforeEach
    void setUp() {
        queryService = mock(QueryService.class);
        agentConversationController = new AgentConversationController(mock(AgentConversationService.class), queryService);
        httpServletRequest = mock(HttpServletRequest.class);
        when(httpServletRequest.getAttribute("appuserId")).thenReturn(APPUSER_ID);
    }

    @Test
    @DisplayName("새 채팅 HTTP 질의는 conversationUuid 없이 새 채팅방 생성을 요청한다")
    void newConversationHttpQueryClearsConversationUuid() {
        QueryRequest request = queryRequest();
        QueryResponse response = QueryResponse.builder().conversationUuid(CONVERSATION_UUID).build();
        when(queryService.answerWithConversation(eq(APPUSER_ID), isNull(), any(QueryRequest.class), eq(false)))
                .thenReturn(response);

        agentConversationController.queryHttp(httpServletRequest, request);

        verify(queryService).answerWithConversation(eq(APPUSER_ID), isNull(), any(QueryRequest.class), eq(false));
    }

    @Test
    @DisplayName("새 채팅 HTTP 테스트 질의는 토큰 사용 없이 테스트 모드로 새 채팅방 생성을 요청한다")
    void newConversationHttpTestQueryUsesTestMode() {
        QueryRequest request = queryRequest();
        QueryResponse response = QueryResponse.builder().conversationUuid(CONVERSATION_UUID).build();
        when(queryService.answerWithConversation(eq(APPUSER_ID), isNull(), any(QueryRequest.class), eq(true)))
                .thenReturn(response);

        agentConversationController.queryTest(httpServletRequest, request);

        verify(queryService).answerWithConversation(eq(APPUSER_ID), isNull(), any(QueryRequest.class), eq(true));
    }

    @Test
    @DisplayName("특정 채팅방 HTTP 질의는 path의 conversationUuid로 이어서 대화한다")
    void existingConversationHttpQueryUsesPathConversationUuid() {
        QueryRequest request = queryRequest();
        QueryResponse response = QueryResponse.builder().conversationUuid(CONVERSATION_UUID).build();
        when(queryService.answerWithConversation(eq(APPUSER_ID), eq(CONVERSATION_UUID), any(QueryRequest.class), eq(false)))
                .thenReturn(response);

        agentConversationController.queryHttpInConversation(httpServletRequest, CONVERSATION_UUID, request);

        verify(queryService).answerWithConversation(eq(APPUSER_ID), eq(CONVERSATION_UUID), any(QueryRequest.class), eq(false));
    }

    @Test
    @DisplayName("새 채팅 SSE 질의는 conversationUuid 없이 새 채팅방 생성을 요청한다")
    void newConversationStreamQueryClearsConversationUuid() {
        QueryRequest request = queryRequest();
        when(queryService.answerFluxWithConversation(eq(APPUSER_ID), isNull(), any(QueryRequest.class)))
                .thenReturn(Flux.empty());

        agentConversationController.query(httpServletRequest, request);

        verify(queryService).answerFluxWithConversation(eq(APPUSER_ID), isNull(), any(QueryRequest.class));
    }

    @Test
    @DisplayName("특정 채팅방 SSE 질의는 path의 conversationUuid로 이어서 대화한다")
    void existingConversationStreamQueryUsesPathConversationUuid() {
        QueryRequest request = queryRequest();
        when(queryService.answerFluxWithConversation(eq(APPUSER_ID), eq(CONVERSATION_UUID), any(QueryRequest.class)))
                .thenReturn(Flux.<ServerSentEvent<String>>empty());

        agentConversationController.queryInConversation(httpServletRequest, CONVERSATION_UUID, request);

        verify(queryService).answerFluxWithConversation(eq(APPUSER_ID), eq(CONVERSATION_UUID), any(QueryRequest.class));
    }

    private QueryRequest queryRequest() {
        QueryRequest request = new QueryRequest();
        request.setQuestion("이 프로젝트 구조 설명해줘");
        request.setArchId("spring-projects__spring-petclinic");
        request.setRepoUuid(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        return request;
    }
}
