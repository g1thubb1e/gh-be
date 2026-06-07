package com.ssafy.githubble.domain.profile.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "github_contribution_calendar")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GithubContributionCalendar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "grass_id")
    private Long grassId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private GithubProfile profile;

    @Column(name = "contribution_date", nullable = false)
    private LocalDate contributionDate;

    @Column(name = "contribution_cnt", nullable = false)
    private Integer contributionCnt;

    @Column(name = "contribution_level", nullable = false, length = 255)
    private String contributionLevel;

    @Column(name = "grass_uuid", nullable = false, unique = true, updatable = false)
    private UUID grassUuid;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    public void generateId() {
        if (this.grassUuid == null) {
            this.grassUuid = UUID.randomUUID();
        }
        if (this.updatedAt == null) {
            this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void updateContribution(Integer contributionCnt, String contributionLevel) {
        this.contributionCnt = contributionCnt;
        this.contributionLevel = contributionLevel;
    }

    @Builder
    public GithubContributionCalendar(
            GithubProfile profile,
            LocalDate contributionDate,
            Integer contributionCnt,
            String contributionLevel
    ) {
        this.profile = profile;
        this.contributionDate = contributionDate;
        this.contributionCnt = contributionCnt;
        this.contributionLevel = contributionLevel;
    }
}
