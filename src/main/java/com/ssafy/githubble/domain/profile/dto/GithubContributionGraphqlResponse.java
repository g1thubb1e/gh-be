package com.ssafy.githubble.domain.profile.dto;

import java.util.List;

// GitHub totalContributions 응답 DTO
public record GithubContributionGraphqlResponse(
        Data data,
        List<GraphqlError> errors
) {
    public record Data(
            User user
    ) {
    }

    public record User(
            ContributionsCollection contributionsCollection
    ) {
    }

    public record ContributionsCollection(
            ContributionCalendar contributionCalendar
    ) {
    }

    public record ContributionCalendar(
            Integer totalContributions
    ) {
    }

    public record GraphqlError(
            String message
    ) {
    }

    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }

    public int commitCount() {
        if (data == null
                || data.user() == null
                || data.user().contributionsCollection() == null
                || data.user().contributionsCollection().contributionCalendar() == null
                || data.user().contributionsCollection().contributionCalendar().totalContributions() == null) {
            return 0;
        }

        return data.user()
                .contributionsCollection()
                .contributionCalendar()
                .totalContributions();
    }
}
