package com.ssafy.githubble.domain.profile.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Getter
@Builder
@Table(name = "github_profile")
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GithubProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "gitprofile_id")
    private Long gitprofileId;

    @Column(name = "appuser_id")
    private Long appuserId;

    @Column
    private String username;

    @Column
    private String name;

    @Column(name = "github_avatar_url")
    private String githubAvatarUrl;

    @Column
    private String type;

    @Column
    private String company;

    @Column
    private String blog;

    @Column
    private String location;

    @Column
    private String email;

    @Column
    private String bio;

    @Column(name = "public_repos_count")
    private Integer publicReposCount;

    @Column
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @Column(name = "this_year_contributions")
    private Integer thisYearContributions;

    @Column(name = "repo_updated_at")
    private OffsetDateTime repoUpdatedAt;

    @Column(name = "gitprofile_uuid", nullable = false, unique = true)
    private String gitprofileUuid;

    @PrePersist
    public void generateId() {
        if (this.gitprofileUuid == null) {
            this.gitprofileUuid = UUID.randomUUID().toString();
        }
    }

    public void updateGithubProfile(
            Long appuserId,
            String username,
            String name,
            String githubAvatarUrl,
            String type,
            String company,
            String blog,
            String location,
            String email,
            String bio,
            Integer publicReposCount,
            LocalDateTime createdAt,
            Integer thisYearContributions
            ) {
        this.appuserId = appuserId;
        this.username = username;
        this.name = name;
        this.githubAvatarUrl = githubAvatarUrl;
        this.type = type;
        this.company = company;
        this.blog = blog;
        this.location = location;
        this.email = email;
        this.bio = bio;
        this.publicReposCount = publicReposCount;
        this.createdAt = createdAt;
        this.thisYearContributions = thisYearContributions;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateRepoUpdatedAt(OffsetDateTime repoUpdatedAt) {
        this.repoUpdatedAt = repoUpdatedAt;
    }

}
