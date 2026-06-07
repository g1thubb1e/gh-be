package com.ssafy.githubble.domain.profile.client;

import com.ssafy.githubble.domain.profile.dto.GithubRepoResponse;
import com.ssafy.githubble.global.code.ErrorCode;
import com.ssafy.githubble.global.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriBuilder;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class GithubRepositoryListClient {

    private final WebClient githubWebClient;

    public GithubRepositoryListClient(WebClient githubWebClient) {
        this.githubWebClient = githubWebClient;
    }

    // 내 레포 목록 조회
    public List<GithubRepoResponse> getMyRepositoryList(
            String githubAccessToken,
            OffsetDateTime since,
            int perPage,
            int page
    ) {
        if (githubAccessToken == null || githubAccessToken.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // 한 페이지에 몇 개, 몇 번째 페이지
        int safePerPage = Math.max(1, Math.min(perPage, 100));
        int safePage = Math.max(1, page);

        return githubWebClient.get()
                .uri(uriBuilder -> {
                    UriBuilder builder = uriBuilder
                            .path("/user/repos")
                            .queryParam("affiliation", "owner,collaborator,organization_member")
                            .queryParam("sort", "updated")
                            .queryParam("direction", "desc")
                            .queryParam("per_page", safePerPage)
                            .queryParam("page", safePage);

                    if (since != null) {
                        builder.queryParam("since", since.withOffsetSameInstant(ZoneOffset.UTC).toInstant().toString());
                    }
                    return builder.build();
                })
                .headers(headers -> headers.setBearerAuth(githubAccessToken))
                .retrieve()
                .bodyToFlux(GithubRepoResponse.class)
                .onErrorMap(WebClientResponseException.class, this::toBusinessException)
                .collectList()
                .block();

    }

    private BusinessException toBusinessException(WebClientResponseException e) {
        int statusCode = e.getStatusCode().value();

        if (statusCode == 401 || statusCode == 403) {
            return new BusinessException(ErrorCode.EXTERNAL_API_UNAUTHORIZED);
        }
        if (statusCode == 404) {
            return new BusinessException(ErrorCode.NOT_FOUND);
        }
        return new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
    }
}
