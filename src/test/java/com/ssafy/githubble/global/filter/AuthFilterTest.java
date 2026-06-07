package com.ssafy.githubble.global.filter;

import com.ssafy.githubble.domain.auth.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AuthFilterTest {

    private final AuthFilter authFilter = new AuthFilter(mock(JwtTokenProvider.class));

    @Test
    @DisplayName("agent qa-answer API는 인증 필터를 건너뛴다")
    void agentQaAnswerSkipsAuthFilter() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/agent/qa-answer");

        assertThat(authFilter.shouldNotFilter(request)).isTrue();
    }

    @Test
    @DisplayName("agent route API는 인증 필터를 건너뛴다")
    void agentRouteSkipsAuthFilter() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/agent/route");

        assertThat(authFilter.shouldNotFilter(request)).isTrue();
    }

    @Test
    @DisplayName("삭제된 legacy agent chat API는 인증 필터를 적용한다")
    void legacyAgentChatUsesAuthFilter() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/agent/chat");

        assertThat(authFilter.shouldNotFilter(request)).isFalse();
    }

    @Test
    @DisplayName("agent conversations API는 인증 필터를 적용한다")
    void agentConversationsUsesAuthFilter() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/agent/conversations");

        assertThat(authFilter.shouldNotFilter(request)).isFalse();
    }

    @Test
    @DisplayName("기존 채팅방 질의 API는 인증 필터를 적용한다")
    void conversationQueryUsesAuthFilter() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/agent/conversations/10/query");

        assertThat(authFilter.shouldNotFilter(request)).isFalse();
    }

    @Test
    @DisplayName("새 채팅방 테스트 질의 API는 인증 필터를 적용한다")
    void conversationTestQueryUsesAuthFilter() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/agent/conversations/query/test");

        assertThat(authFilter.shouldNotFilter(request)).isFalse();
    }

    @Test
    @DisplayName("Health actuator endpoint는 인증 필터를 건너뛴다")
    void actuatorHealthSkipsAuthFilter() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/actuator/health");

        assertThat(authFilter.shouldNotFilter(request)).isTrue();
    }

    @Test
    @DisplayName("Prometheus actuator endpoint는 인증 필터를 건너뛴다")
    void actuatorPrometheusSkipsAuthFilter() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/actuator/prometheus");

        assertThat(authFilter.shouldNotFilter(request)).isTrue();
    }
}
