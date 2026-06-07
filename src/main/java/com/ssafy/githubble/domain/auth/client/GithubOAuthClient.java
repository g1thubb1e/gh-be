package com.ssafy.githubble.domain.auth.client;

import com.ssafy.githubble.global.code.ErrorCode;
import com.ssafy.githubble.global.exception.BusinessException;
import com.ssafy.githubble.global.properties.GithubOAuthProperties;
import com.ssafy.githubble.domain.auth.dto.GithubAccessTokenResponse;
import com.ssafy.githubble.domain.auth.dto.GithubEmailResponse;
import com.ssafy.githubble.domain.auth.dto.GithubUserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class GithubOAuthClient {
    private final GithubOAuthProperties properties;
    private final RestClient restClient = RestClient.create();

    public String exchangeCodeForAccessToken(String code) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", properties.getClientId());
        body.add("client_secret", properties.getClientSecret());
        body.add("code", code);
        body.add("redirect_uri", properties.getRedirectUri());

        GithubAccessTokenResponse response = restClient.post()
                .uri("https://github.com/login/oauth/access_token")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(GithubAccessTokenResponse.class);

        if (response == null || response.accessToken() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "github access token 발급에 실패하였습니다.");
        }
        return response.accessToken();
    }

    public GithubUserResponse fetchUser(String githubAccessToken) {
        GithubUserResponse response = restClient.get()
                .uri("https://api.github.com/user")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + githubAccessToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(GithubUserResponse.class);

        if (response == null || response.id() == null) {
            throw new IllegalStateException("Failed to fetch GitHub user.");
        }
        return response;
    }

    public Optional<String> fetchPrimaryVerifiedEmail(String githubAccessToken) {
        List<GithubEmailResponse> emails = restClient.get()
                .uri("https://api.github.com/user/emails")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + githubAccessToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(new ParameterizedTypeReference<List<GithubEmailResponse>>() {
                });

        if (emails == null) {
            return Optional.empty();
        }

        return emails.stream()
                .filter(GithubEmailResponse::verified)
                .sorted(Comparator.comparing(GithubEmailResponse::primary).reversed())
                .map(GithubEmailResponse::email)
                .findFirst();
    }

    public void revokeAuthorization(String githubAccessToken) {
        if (githubAccessToken == null) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "github access token이 존재하지 않습니다.");
        }

        try {
            restClient.method(HttpMethod.DELETE)
                    .uri("https://api.github.com/applications/{clientId}/grant", properties.getClientId())
                    .headers(headers -> {
                        headers.setBasicAuth(properties.getClientId(), properties.getClientSecret());
                        headers.setAccept(List.of(MediaType.valueOf("application/vnd.github+json")));
                        headers.set("X-GitHub-Api-Version", "2022-11-28");
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("access_token", githubAccessToken))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "github authorization revoke에 실패하였습니다.");
        }
    }
}
