package com.ssafy.githubble.domain.profile.client;

import com.ssafy.githubble.domain.profile.dto.GithubUserApiResponse;
import com.ssafy.githubble.global.code.ErrorCode;
import com.ssafy.githubble.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
@RequiredArgsConstructor
public class GithubProfileClient {

    private final WebClient githubWebClient;

    public GithubUserApiResponse getUser(String githubAccessToken, String username) {
        if (githubAccessToken == null || githubAccessToken.isBlank() || username == null || username.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        return githubWebClient.get()
                .uri("/users/{username}", username)
                .headers(headers -> headers.setBearerAuth(githubAccessToken))
                .retrieve()
                .bodyToMono(GithubUserApiResponse.class)
                .onErrorMap(WebClientResponseException.class, this::toBusinessException)
                .block();
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
