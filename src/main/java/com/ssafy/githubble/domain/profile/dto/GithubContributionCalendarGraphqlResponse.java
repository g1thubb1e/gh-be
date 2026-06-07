package com.ssafy.githubble.domain.profile.dto;

import java.util.List;

// GitHub contributionCalendar(잔디) 응답 DTO
public record GithubContributionCalendarGraphqlResponse(
        Data data,
        List<GraphqlError> errors
) {
    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }

    public record Data(
            User user
    ) {}

    public record User(
            ContributionsCollection contributionsCollection
    ) {}

    public record ContributionsCollection(
            ContributionCalendar contributionCalendar
    ) {}

    public record ContributionCalendar(
            Integer totalContributions,
            List<Week> weeks
    ) {}

    public record Week(
            List<ContributionDay> contributionDays
    ) {}

    public record ContributionDay(
            String date, // yyyy-MM-dd
            Integer contributionCount, // 해당 날짜 기여 수
            String contributionLevel // NONE, FIRST_QUARTILE, SECOND_QUARTILE, THIRD_QUARTILE, FOURTH_QUARTILE
    ) {}

    public record GraphqlError(
            String message
    ) {}
}
