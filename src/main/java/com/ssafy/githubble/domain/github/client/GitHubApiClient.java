package com.ssafy.githubble.domain.github.client;

import com.ssafy.githubble.domain.github.dto.external.GitHubContentApiResponse;
import com.ssafy.githubble.domain.github.dto.external.GitHubBranchApiResponse;
import com.ssafy.githubble.domain.github.dto.external.GitHubRepositoryApiResponse;
import com.ssafy.githubble.domain.github.dto.external.GitHubTreeApiResponse;
import com.ssafy.githubble.global.code.ErrorCode;
import com.ssafy.githubble.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class GitHubApiClient {

    private static final Pattern LAST_PAGE_PATTERN = Pattern.compile("[?&]page=(\\d+)[^>]*>\\s*;\\s*rel=\\\"last\\\"");

    private final WebClient githubWebClient;

    public GitHubApiClient(WebClient githubWebClient) {
        this.githubWebClient = githubWebClient;
    }

    public GitHubRepositoryApiResponse getRepository(String githubAccessToken, String owner, String repo) {
        return executeGitHubCall("repository_metadata", owner, repo, null, () -> githubWebClient.get()
                .uri("/repos/{owner}/{repo}", owner, repo)
                .headers(headers -> headers.setBearerAuth(githubAccessToken))
                .retrieve()
                .bodyToMono(GitHubRepositoryApiResponse.class)
                .block());
    }

    // 브랜치의 가장 최근 커밋 가져오는 API
    public GitHubBranchApiResponse getBranch(String githubAccessToken, String owner, String repo, String branch) {
        return executeGitHubCall("branch_latest_commit", owner, repo, branch, () -> githubWebClient.get()
                .uri("/repos/{owner}/{repo}/branches/{branch}", owner, repo, branch)
                .headers(headers -> headers.setBearerAuth(githubAccessToken))
                .retrieve()
                .bodyToMono(GitHubBranchApiResponse.class)
                .block());
    }

    public Map<String, Long> getLanguages(String githubAccessToken, String owner, String repo) {
        return executeGitHubCall("language_statistics", owner, repo, null, () -> githubWebClient.get()
                .uri("/repos/{owner}/{repo}/languages", owner, repo)
                .headers(headers -> headers.setBearerAuth(githubAccessToken))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Long>>() {
                })
                .block());
    }

    public long countOpenPullRequests(String githubAccessToken, String owner, String repo) {
        ResponseEntity<List<Map<String, Object>>> response = executeGitHubCall("issue_pr_count", owner, repo, null, () -> githubWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/{owner}/{repo}/pulls")
                        .queryParam("state", "open")
                        .queryParam("per_page", "1")
                        .build(owner, repo))
                .headers(headers -> headers.setBearerAuth(githubAccessToken))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                })
                .block());

        if (response == null) {
            return 0L;
        }

        long lastPage = extractLastPage(response.getHeaders());
        if (lastPage >= 0L) {
            return lastPage;
        }

        List<Map<String, Object>> body = response.getBody();
        return body == null ? 0L : body.size();
    }

    private long extractLastPage(HttpHeaders headers) {
        String link = headers.getFirst(HttpHeaders.LINK);
        if (link == null || link.isBlank()) {
            return -1L;
        }

        Matcher matcher = LAST_PAGE_PATTERN.matcher(link);
        if (!matcher.find()) {
            return -1L;
        }

        return Long.parseLong(matcher.group(1));
    }

    public GitHubTreeApiResponse getTree(String githubAccessToken, String owner, String repo, String branch) {
        return executeGitHubCall("tree_load", owner, repo, branch, () -> githubWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/{owner}/{repo}/git/trees/{branch}")
                        .queryParam("recursive", "1")
                        .build(owner, repo, branch))
                .headers(headers -> headers.setBearerAuth(githubAccessToken))
                .retrieve()
                .bodyToMono(GitHubTreeApiResponse.class)
                .block());
    }

    public Optional<GitHubContentApiResponse> getReadme(String githubAccessToken, String owner, String repo) {
        long startedAt = System.currentTimeMillis();
        logGitHubApiStarted("readme_load", owner, repo, null);
        try {
            GitHubContentApiResponse response = githubWebClient.get()
                    .uri("/repos/{owner}/{repo}/readme", owner, repo)
                    .headers(headers -> headers.setBearerAuth(githubAccessToken))
                    .retrieve()
                    .bodyToMono(GitHubContentApiResponse.class)
                    .block();
            logGitHubApiSucceeded("readme_load", owner, repo, null, startedAt);
            return Optional.ofNullable(response);
        } catch (WebClientResponseException.NotFound e) {
            logGitHubApiFailed("readme_load", owner, repo, null, startedAt, e);
            return Optional.empty();
        } catch (Throwable e) {
            logGitHubApiFailed("readme_load", owner, repo, null, startedAt, e);
            throw toBusinessException(e);
        }
    }

    public GitHubContentApiResponse getContent(String githubAccessToken, String owner, String repo, String path, String ref) {
        return executeGitHubCall("content_load", owner, repo, ref, () -> githubWebClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/repos/{owner}/{repo}/contents/{path}");
                    if (ref != null && !ref.isBlank()) {
                        builder.queryParam("ref", ref);
                    }
                    return builder.build(owner, repo, path);
                })
                .headers(headers -> {
                    headers.setBearerAuth(githubAccessToken);
                    headers.set(HttpHeaders.ACCEPT, "application/vnd.github.object+json");
                })
                .retrieve()
                .bodyToMono(GitHubContentApiResponse.class)
                .block());
    }

    private <T> T executeGitHubCall(String endpointName, String owner, String repo, String ref, Supplier<T> call) {
        long startedAt = System.currentTimeMillis();
        logGitHubApiStarted(endpointName, owner, repo, ref);
        try {
            T response = call.get();
            logGitHubApiSucceeded(endpointName, owner, repo, ref, startedAt);
            return response;
        } catch (Throwable e) {
            logGitHubApiFailed(endpointName, owner, repo, ref, startedAt, e);
            throw toBusinessException(e);
        }
    }

    private void logGitHubApiStarted(String endpointName, String owner, String repo, String ref) {
        log.info("github.api.event event=started endpointName={} owner={} repo={} ref={}",
                endpointName, owner, repo, logValue(ref));
    }

    private void logGitHubApiSucceeded(String endpointName, String owner, String repo, String ref, long startedAt) {
        log.info("github.api.event event=succeeded endpointName={} owner={} repo={} ref={} status={} elapsedMs={} responseBytes={}",
                endpointName, owner, repo, logValue(ref), "-", elapsedMs(startedAt), -1);
    }

    private void logGitHubApiFailed(String endpointName, String owner, String repo, String ref, long startedAt, Throwable e) {
        log.warn("github.api.event event=failed endpointName={} owner={} repo={} ref={} status={} elapsedMs={} responseBytes={} errorCode={} exceptionType={}",
                endpointName,
                owner,
                repo,
                logValue(ref),
                status(e),
                elapsedMs(startedAt),
                responseBytes(e),
                githubErrorCode(e),
                e.getClass().getName());
    }

    private long elapsedMs(long startedAt) {
        return System.currentTimeMillis() - startedAt;
    }

    private String logValue(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String status(Throwable e) {
        if (e instanceof WebClientResponseException responseException) {
            return String.valueOf(responseException.getStatusCode().value());
        }
        return "-";
    }

    private int responseBytes(Throwable e) {
        if (e instanceof WebClientResponseException responseException) {
            return responseException.getResponseBodyAsByteArray().length;
        }
        return -1;
    }

    private String githubErrorCode(Throwable e) {
        if (isCausedBy(e, DataBufferLimitException.class)) {
            return "github_response_too_large";
        }
        if (isTimeout(e)) {
            return "github_timeout";
        }
        if (isCausedBy(e, DecodingException.class)) {
            return "github_decode_failed";
        }
        if (e instanceof WebClientResponseException responseException) {
            int status = responseException.getStatusCode().value();
            if (isRateLimited(responseException)) {
                return "github_rate_limited";
            }
            if (status == 401) {
                return "github_http_401";
            }
            if (status == 403) {
                return "github_http_403";
            }
            if (status == 404) {
                return "github_http_404";
            }
        }
        return "github_unknown_failure";
    }

    private boolean isRateLimited(WebClientResponseException e) {
        String remaining = e.getHeaders().getFirst("X-RateLimit-Remaining");
        return (e.getStatusCode().value() == 403 || e.getStatusCode().value() == 429)
                && "0".equals(remaining);
    }

    private boolean isTimeout(Throwable e) {
        return isCausedBy(e, TimeoutException.class)
                || isCausedBy(e, SocketTimeoutException.class)
                || hasCauseClassNameContaining(e, "Timeout");
    }

    private boolean isCausedBy(Throwable e, Class<? extends Throwable> type) {
        Throwable current = e;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean hasCauseClassNameContaining(Throwable e, String keyword) {
        Throwable current = e;
        while (current != null) {
            if (current.getClass().getName().contains(keyword)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private BusinessException toBusinessException(Throwable e) {
        if (e instanceof WebClientResponseException responseException) {
            return toBusinessException(responseException);
        }
        return new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
    }

    private BusinessException toBusinessException(WebClientResponseException e) {
        if (e.getStatusCode().value() == 404) {
            return new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
            return new BusinessException(ErrorCode.EXTERNAL_API_UNAUTHORIZED);
        }
        return new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
    }
}
