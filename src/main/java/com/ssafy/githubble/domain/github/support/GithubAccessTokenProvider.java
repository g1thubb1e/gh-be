package com.ssafy.githubble.domain.github.support;

import com.ssafy.githubble.domain.auth.domain.User;
import com.ssafy.githubble.domain.auth.repository.UserRepository;
import com.ssafy.githubble.global.code.ErrorCode;
import com.ssafy.githubble.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
@RequiredArgsConstructor
public class GithubAccessTokenProvider {

    private final UserRepository userRepository;
    private final WebClient githubWebClient;

    public String getGithubAccessToken(Long appuserId) {
        if (appuserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "다시 로그인해주세요.");
        }

        User user = userRepository.findById(appuserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "다시 로그인해주세요."));
        String githubAccessToken = user.getGithubAccessToken();
        if (githubAccessToken == null || githubAccessToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "GitHub 재로그인이 필요합니다.");
        }

        validateGithubAccessToken(githubAccessToken);

        return githubAccessToken;
    }

    // 깃허브 사용자 조회 API로 토큰 검증
    private void validateGithubAccessToken(String githubAccessToken) {
        try {
            githubWebClient.get()
                    .uri("/user")
                    .headers(headers -> headers.setBearerAuth(githubAccessToken))
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (WebClientResponseException e) {
            throw toBusinessException(e);
        }
    }

    private BusinessException toBusinessException(WebClientResponseException e) {
        if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
            return new BusinessException(ErrorCode.EXTERNAL_API_UNAUTHORIZED);
        }
        return new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
    }

}
