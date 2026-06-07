package com.ssafy.githubble.domain.profile.client;

import com.ssafy.githubble.domain.profile.dto.GithubRepoResponse;
import com.ssafy.githubble.domain.profile.dto.GithubRepoSearchResponse;
import com.ssafy.githubble.global.code.ErrorCode;
import com.ssafy.githubble.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class GithubPopularRepositoryClient {

    private final WebClient githubWebClient;

    public GithubRepoResponse getMostPopularRepository(String githubAccessToken, String username) {
        if (githubAccessToken == null || githubAccessToken.isBlank()
                || username == null || username.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        GithubRepoSearchResponse response = githubWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search/repositories")
                        .queryParam("q", "user:" + username)
                        .queryParam("sort", "stars")
                        .queryParam("order", "desc")
                        .queryParam("per_page", 100)
                        .queryParam("page", 1)
                        .build())
                .headers(headers -> headers.setBearerAuth(githubAccessToken))
                .retrieve()
                .bodyToMono(GithubRepoSearchResponse.class)
                .onErrorMap(WebClientResponseException.class, this::toBusinessException)
                .block();

        if (response == null || response.items() == null || response.items().isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "대표 레포가 없습니다.");
        }

        List<GithubRepoResponse> items = response.items();

        // GitHub stars 내림차순 결과의 첫 원소 star를 topStar로 사용
        int topStar = items.getFirst().stargazersCount() == null ? 0 : items.getFirst().stargazersCount();

        // 동률(topStar) 그룹 안에서만 createdAt 최신 1개 선택
        return items.stream()
                .filter(repo -> (repo.stargazersCount() == null ? 0 : repo.stargazersCount()) == topStar)
                .max(Comparator.comparing(
                        GithubRepoResponse::createdAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())
                ))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "대표 레포가 없습니다."));
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
