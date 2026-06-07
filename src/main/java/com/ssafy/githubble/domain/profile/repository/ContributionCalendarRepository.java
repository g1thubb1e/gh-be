package com.ssafy.githubble.domain.profile.repository;

import com.ssafy.githubble.domain.profile.domain.GithubContributionCalendar;
import com.ssafy.githubble.domain.profile.domain.GithubProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ContributionCalendarRepository extends JpaRepository<GithubContributionCalendar, Long> {

    Optional<GithubContributionCalendar> findTopByProfileOrderByUpdatedAtDesc(GithubProfile profile);

    Optional<GithubContributionCalendar> findByProfileAndContributionDate(
            GithubProfile profile,
            LocalDate contributionDate
    );

    List<GithubContributionCalendar> findAllByProfileAndContributionDateBetweenOrderByContributionDateAsc(
            GithubProfile profile,
            LocalDate fromDate,
            LocalDate toDate
    );
}
