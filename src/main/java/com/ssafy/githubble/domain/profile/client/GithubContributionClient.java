package com.ssafy.githubble.domain.profile.client;

import com.ssafy.githubble.domain.profile.dto.GithubContributionCalendarGraphqlResponse;
import com.ssafy.githubble.domain.profile.dto.GithubContributionGraphqlRequest;
import com.ssafy.githubble.domain.profile.dto.GithubContributionGraphqlResponse;
import com.ssafy.githubble.global.code.ErrorCode;
import com.ssafy.githubble.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
public class GithubContributionClient {

    private final WebClient githubWebClient;

    private static final String COMMIT_COUNT_QUERY = """
            query($username: String!, $from: DateTime!, $to: DateTime!) {
              user(login: $username) {
                contributionsCollection(from: $from, to: $to) {
                  contributionCalendar {
                    totalContributions
                  }
                }
              }
            }
            """;

    private static final String CONTRIBUTION_CALENDAR_QUERY = """
            query($username: String!, $from: DateTime!, $to: DateTime!) {
              user(login: $username) {
                contributionsCollection(from: $from, to: $to) {
                  contributionCalendar {
                    totalContributions
                    weeks {
                      contributionDays {
                        date
                        contributionCount
                        contributionLevel
                      }
                    }
                  }
                }
              }
            }
            """;


    public int getCommitCount(
            String githubAccessToken,
            String username,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        if (githubAccessToken == null || githubAccessToken.isBlank()
                || username == null || username.isBlank()
                || from == null || to == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        GithubContributionGraphqlRequest request = GithubContributionGraphqlRequest.of(
                COMMIT_COUNT_QUERY,
                username,
                from.toInstant().toString(),
                to.toInstant().toString()
        );

        GithubContributionGraphqlResponse response = githubWebClient.post()
                .uri("/graphql")
                .headers(headers -> headers.setBearerAuth(githubAccessToken))
                .bodyValue(request)
                .retrieve()
                .bodyToMono(GithubContributionGraphqlResponse.class)
                .onErrorMap(WebClientResponseException.class, this::toBusinessException)
                .block();

        if (response == null || response.hasErrors()) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
        }

        return response.commitCount();
    }

    public GithubContributionCalendarGraphqlResponse.ContributionCalendar getContributionCalendar(
            String githubAccessToken,
            String username,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        if (githubAccessToken == null || githubAccessToken.isBlank()
                || username == null || username.isBlank()
                || from == null || to == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        GithubContributionGraphqlRequest request = GithubContributionGraphqlRequest.of(
                CONTRIBUTION_CALENDAR_QUERY,
                username,
                from.toInstant().toString(),
                to.toInstant().toString()
        );

        GithubContributionCalendarGraphqlResponse response = githubWebClient.post()
                .uri("/graphql")
                .headers(headers -> headers.setBearerAuth(githubAccessToken))
                .bodyValue(request)
                .retrieve()
                .bodyToMono(GithubContributionCalendarGraphqlResponse.class)
                .onErrorMap(WebClientResponseException.class, this::toBusinessException)
                .block();

        if (response == null || response.hasErrors()
                || response.data() == null
                || response.data().user() == null
                || response.data().user().contributionsCollection() == null
                || response.data().user().contributionsCollection().contributionCalendar() == null) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
        }

        return response.data().user().contributionsCollection().contributionCalendar();
    }

    // 예외 처리
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
