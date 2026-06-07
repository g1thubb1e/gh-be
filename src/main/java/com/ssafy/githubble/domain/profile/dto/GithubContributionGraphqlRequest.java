package com.ssafy.githubble.domain.profile.dto;

// GitHub totalCommitContributions, contributionCalendar 요청 DTO
public record GithubContributionGraphqlRequest(
        String query,
        Variables variables
) {
    public record Variables(
            String username,
            String from,
            String to
    ) {
    }

    public static GithubContributionGraphqlRequest of(
            String query,
            String username,
            String from,
            String to
    ) {
        return new GithubContributionGraphqlRequest(
                query,
                new Variables(username, from, to)
        );
    }
}
