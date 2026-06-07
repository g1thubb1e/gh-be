package com.ssafy.githubble.global.filter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessLogFilterTest {

    private final AccessLogFilter accessLogFilter = new AccessLogFilter();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("requestId header가 없으면 새 requestId를 만들고 response header와 access log에 남긴다")
    void createsRequestIdWhenHeaderMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/repositories");
        request.setAttribute("appuserId", 42L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> ((MockHttpServletResponse) res).setStatus(201);
        ListAppender<ILoggingEvent> appender = attachListAppender();

        accessLogFilter.doFilter(request, response, chain);

        String requestId = response.getHeader(AccessLogFilter.REQUEST_ID_HEADER);
        assertThat(requestId).isNotBlank();
        assertThat(UUID.fromString(requestId)).isNotNull();
        assertThat(accessLogEvents(appender)).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage())
                    .contains("http.access")
                    .contains("requestId=" + requestId)
                    .contains("method=GET")
                    .contains("uri=/api/v1/repositories")
                    .contains("status=201")
                    .contains("appuserId=42")
                    .contains("elapsedMs=")
                    .doesNotContain("Authorization", "Cookie", "requestBody", "responseBody");
        });
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    @DisplayName("X-Request-Id header가 있으면 기존 값을 유지한다")
    void keepsIncomingRequestId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/repositories");
        request.addHeader(AccessLogFilter.REQUEST_ID_HEADER, "client-request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        ListAppender<ILoggingEvent> appender = attachListAppender();

        accessLogFilter.doFilter(request, response, chain);

        assertThat(response.getHeader(AccessLogFilter.REQUEST_ID_HEADER)).isEqualTo("client-request-123");
        assertThat(accessLogEvents(appender)).singleElement().satisfies(event ->
                assertThat(event.getFormattedMessage()).contains("requestId=client-request-123"));
    }

    @Test
    @DisplayName("X-Request-Id가 안전하지 않은 형식이면 새 requestId를 생성한다")
    void regeneratesUnsafeIncomingRequestId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/repositories");
        request.addHeader(AccessLogFilter.REQUEST_ID_HEADER, "client-request-123\nforged=true");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ListAppender<ILoggingEvent> appender = attachListAppender();

        accessLogFilter.doFilter(request, response, new MockFilterChain());

        String requestId = response.getHeader(AccessLogFilter.REQUEST_ID_HEADER);
        assertThat(requestId).isNotEqualTo("client-request-123\nforged=true");
        assertThat(UUID.fromString(requestId)).isNotNull();
        assertThat(accessLogEvents(appender)).singleElement().satisfies(event ->
                assertThat(event.getFormattedMessage())
                        .contains("requestId=" + requestId)
                        .doesNotContain("forged=true"));
    }

    @Test
    @DisplayName("appuserId가 예상 scalar 타입이 아니면 toString 결과를 access log에 남기지 않는다")
    void doesNotLogNonScalarAppuserId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/repositories");
        request.setAttribute("appuserId", new Object() {
            @Override
            public String toString() {
                return "user-secret-object";
            }
        });
        MockHttpServletResponse response = new MockHttpServletResponse();
        ListAppender<ILoggingEvent> appender = attachListAppender();

        accessLogFilter.doFilter(request, response, new MockFilterChain());

        assertThat(accessLogEvents(appender)).singleElement().satisfies(event ->
                assertThat(event.getFormattedMessage())
                        .contains("appuserId=-")
                        .doesNotContain("user-secret-object"));
    }

    @Test
    @DisplayName("filter chain 예외가 발생해도 exception summary를 로그로 남기고 예외를 다시 던진다")
    void logsExceptionSummaryAndRethrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/repositories");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            throw new IllegalStateException("boom-secret-token");
        };
        ListAppender<ILoggingEvent> appender = attachListAppender();

        assertThatThrownBy(() -> accessLogFilter.doFilter(request, response, chain))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom-secret-token");

        assertThat(accessLogEvents(appender)).singleElement().satisfies(event ->
                assertThat(event.getFormattedMessage())
                        .contains("http.access")
                        .contains("status=500")
                        .contains("exceptionType=java.lang.IllegalStateException")
                        .contains("errorCode=INTERNAL_SERVER_ERROR")
                        .doesNotContain("boom-secret-token"));
    }

    @Test
    @DisplayName("health/prometheus actuator endpoint는 INFO access log에서 제외한다")
    void excludesActuatorHealthAndPrometheusFromInfoAccessLog() throws ServletException, IOException {
        ListAppender<ILoggingEvent> appender = attachListAppender();

        accessLogFilter.doFilter(new MockHttpServletRequest("GET", "/api/actuator/health"), new MockHttpServletResponse(), new MockFilterChain());
        accessLogFilter.doFilter(new MockHttpServletRequest("GET", "/api/actuator/prometheus"), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(accessLogEvents(appender)).isEmpty();
    }

    @Test
    @DisplayName("Authorization/Cookie/본문 값을 access log 메시지에 포함하지 않는다")
    void doesNotLogAuthorizationCookieOrBodies() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/repositories");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer access-token-secret");
        request.addHeader(HttpHeaders.COOKIE, "accessToken=cookie-secret");
        request.setContent("request-body-secret".getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();
        ListAppender<ILoggingEvent> appender = attachListAppender();

        accessLogFilter.doFilter(request, response, new MockFilterChain());

        assertThat(accessLogEvents(appender)).singleElement().satisfies(event ->
                assertThat(event.getFormattedMessage())
                        .doesNotContain("access-token-secret")
                        .doesNotContain("cookie-secret")
                        .doesNotContain("request-body-secret")
                        .doesNotContain("Authorization")
                        .doesNotContain("Cookie"));
    }

    private ListAppender<ILoggingEvent> attachListAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(AccessLogFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private List<ILoggingEvent> accessLogEvents(ListAppender<ILoggingEvent> appender) {
        return appender.list.stream()
                .filter(event -> event.getFormattedMessage().contains("http.access"))
                .toList();
    }
}
