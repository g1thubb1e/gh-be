package com.ssafy.githubble.domain.github.client;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ssafy.githubble.global.code.ErrorCode;
import com.ssafy.githubble.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitHubApiClientTest {

    private static final String TOKEN = "gho_secret_token_should_not_be_logged";

    @Test
    @DisplayName("GitHub rate limit 실패를 endpoint/owner/repo/status/elapsed/bytes와 함께 안전하게 로깅한다")
    void logsGithubRateLimitFailureWithoutTokenOrBody() {
        GitHubApiClient client = new GitHubApiClient(WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.FORBIDDEN)
                        .header("X-RateLimit-Remaining", "0")
                        .body("{\"message\":\"API rate limit exceeded secret-body\"}")
                        .build()))
                .build());
        ListAppender<ILoggingEvent> appender = attachListAppender();

        assertThatThrownBy(() -> client.getRepository(TOKEN, "owner", "repo"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EXTERNAL_API_UNAUTHORIZED);

        assertThat(githubApiEvents(appender)).anySatisfy(event ->
                assertThat(event.getFormattedMessage())
                        .contains("github.api.event")
                        .contains("event=failed")
                        .contains("endpointName=repository_metadata")
                        .contains("owner=owner")
                        .contains("repo=repo")
                        .contains("status=403")
                        .contains("errorCode=github_rate_limited")
                        .contains("elapsedMs=")
                        .contains("responseBytes=")
                        .doesNotContain(TOKEN)
                        .doesNotContain("secret-body")
                        .doesNotContain("API rate limit exceeded"));
    }

    @Test
    @DisplayName("GitHub 응답 버퍼 초과를 github_response_too_large로 구분해 로깅한다")
    void logsGithubResponseTooLargeFailure() {
        GitHubApiClient client = new GitHubApiClient(WebClient.builder()
                .exchangeFunction(request -> Mono.error(new DataBufferLimitException("Exceeded limit secret-buffer")))
                .build());
        ListAppender<ILoggingEvent> appender = attachListAppender();

        assertThatThrownBy(() -> client.getTree(TOKEN, "owner", "repo", "main"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EXTERNAL_API_ERROR);

        assertThat(githubApiEvents(appender)).anySatisfy(event ->
                assertThat(event.getFormattedMessage())
                        .contains("github.api.event")
                        .contains("event=failed")
                        .contains("endpointName=tree_load")
                        .contains("owner=owner")
                        .contains("repo=repo")
                        .contains("ref=main")
                        .contains("errorCode=github_response_too_large")
                        .contains("exceptionType=org.springframework.core.io.buffer.DataBufferLimitException")
                        .doesNotContain(TOKEN)
                        .doesNotContain("secret-buffer"));
    }

    private ListAppender<ILoggingEvent> attachListAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(GitHubApiClient.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private List<ILoggingEvent> githubApiEvents(ListAppender<ILoggingEvent> appender) {
        return appender.list.stream()
                .filter(event -> event.getFormattedMessage().contains("github.api.event"))
                .toList();
    }
}
