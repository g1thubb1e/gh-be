package com.ssafy.githubble.domain.profile.service;

import com.ssafy.githubble.domain.auth.domain.User;
import com.ssafy.githubble.domain.auth.repository.UserRepository;
import com.ssafy.githubble.domain.github.support.GithubAccessTokenProvider;
import com.ssafy.githubble.domain.profile.client.GithubContributionClient;
import com.ssafy.githubble.domain.profile.domain.GithubContributionCalendar;
import com.ssafy.githubble.domain.profile.domain.GithubProfile;
import com.ssafy.githubble.domain.profile.dto.ContributionCalendarResponse;
import com.ssafy.githubble.domain.profile.dto.GithubContributionCalendarGraphqlResponse;
import com.ssafy.githubble.domain.profile.repository.ContributionCalendarRepository;
import com.ssafy.githubble.domain.profile.repository.ProfileRepository;
import com.ssafy.githubble.global.code.ErrorCode;
import com.ssafy.githubble.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProfileContributionService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final ContributionCalendarRepository contributionCalendarRepository;
    private final GithubAccessTokenProvider githubAccessTokenProvider;
    private final GithubContributionClient githubContributionClient;

    @Qualifier("jpaTransactionTemplate")
    private final TransactionTemplate transactionTemplate;

    // 나의 잔디 조회 (최근 6개월, updatedAt 기반 + upsert)
    public ContributionCalendarResponse getMyContributionCalendar(Long appuserId) {
        User me = userRepository.findById(appuserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "다시 로그인해주세요."));

        if (Boolean.TRUE.equals(me.getIsDeleted())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "비활성화된 계정입니다.");
        }

        if (me.getUsername() == null || me.getUsername().isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "GitHub 재로그인이 필요합니다.");
        }

        GithubProfile profile = profileRepository.findByUsername(me.getUsername())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "프로필이 없습니다."));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime rangeStart = now.minusMonths(6).withHour(0).withMinute(0).withSecond(0).withNano(0);

        // 마지막 동기화 기준 시각(updatedAt) 조회 -> 6개월 이전이라면 rangeStart
        OffsetDateTime from = contributionCalendarRepository
                .findTopByProfileOrderByUpdatedAtDesc(profile)
                .map(GithubContributionCalendar::getUpdatedAt)
                .map(last -> last.isBefore(rangeStart) ? rangeStart : last)
                .orElse(rangeStart);

        // 깃허브 API 조회
        if (!from.isAfter(now)) { // 서버 시간 이상치 방어용
            String token = githubAccessTokenProvider.getGithubAccessToken(appuserId);
            GithubContributionCalendarGraphqlResponse.ContributionCalendar calendar =
                    fetchCalendar(token, me.getUsername(), from, now);

            List<ContributionCalendarResponse.ContributionDay> fetchedDays = flatten(calendar);

            // 업데이트 및 저장
            transactionTemplate.executeWithoutResult(status -> {
                for (ContributionCalendarResponse.ContributionDay day : fetchedDays) {
                    LocalDate date = LocalDate.parse(day.contributionDate());

                    contributionCalendarRepository.findByProfileAndContributionDate(profile, date)
                            .ifPresentOrElse(
                                    existing -> existing.updateContribution(
                                            day.contributionCnt(),
                                            day.contributionLevel()
                                    ),
                                    () -> contributionCalendarRepository.save(
                                            GithubContributionCalendar.builder()
                                                    .profile(profile)
                                                    .contributionDate(date)
                                                    .contributionCnt(day.contributionCnt())
                                                    .contributionLevel(day.contributionLevel())
                                                    .build()
                                    )
                            );
                }
            });
        }

        // 응답은 최근 6개월만
        List<ContributionCalendarResponse.ContributionDay> days = contributionCalendarRepository
                .findAllByProfileAndContributionDateBetweenOrderByContributionDateAsc(
                        profile, rangeStart.toLocalDate(), now.toLocalDate()
                )
                .stream()
                .map(ContributionCalendarResponse.ContributionDay::fromEntity)
                .toList();

        int totalContributions = days.stream()
                .mapToInt(ContributionCalendarResponse.ContributionDay::contributionCnt)
                .sum();

        return new ContributionCalendarResponse(me.getUsername(), totalContributions, days);
    }


    // 깃허브 API 잔디 조회 요청
    private GithubContributionCalendarGraphqlResponse.ContributionCalendar fetchCalendar(
            String token,
            String username,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        return githubContributionClient.getContributionCalendar(token, username, from, to);
    }

    // Github 중첩 응답(weeks(contributionDays)) -> 프론트 응답용 1차원 리스트
    private List<ContributionCalendarResponse.ContributionDay> flatten(
            GithubContributionCalendarGraphqlResponse.ContributionCalendar calendar
    ) {
        if (calendar == null || calendar.weeks() == null) {
            return List.of();
        }

        return calendar.weeks().stream()
                .filter(week -> week.contributionDays() != null)
                .flatMap(week -> week.contributionDays().stream())
                .filter(day -> day.contributionCount() != null && day.contributionCount() > 0)
                .map(day -> new ContributionCalendarResponse.ContributionDay(
                        day.date(),
                        day.contributionCount(),
                        day.contributionLevel()
                ))
                .toList();
    }
}
