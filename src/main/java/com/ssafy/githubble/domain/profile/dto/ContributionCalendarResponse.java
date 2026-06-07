package com.ssafy.githubble.domain.profile.dto;

import com.ssafy.githubble.domain.profile.domain.GithubContributionCalendar;

import java.util.List;

// 잔디 프론트 응답
public record ContributionCalendarResponse(
        String username,
        int totalContributions,
        List<ContributionDay> days
) {
    public record ContributionDay(
            String contributionDate,
            int contributionCnt,
            String contributionLevel
    ) {
        public static ContributionDay fromEntity(GithubContributionCalendar entity) {
            return new ContributionDay(
                    entity.getContributionDate().toString(),
                    entity.getContributionCnt(),
                    entity.getContributionLevel()
            );
        }
    }
}
