package com.ssafy.githubble.domain.profile.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "my_github_repository",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_profile_github_repo_id",
                        columnNames = {"profile_id", "repo_name"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MyGithubRepository {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "repo_id")
    private Long repoId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private GithubProfile profile;

    @Column(name = "username", nullable = false, length = 255)
    private String username;

    @Column(name = "repo_name", nullable = false, length = 255)
    private String repoName;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "html_url", nullable = false, length = 255)
    private String htmlUrl;

    @Column(name = "language", length = 255)
    private String language;

    @Column(name = "stargazer_cnt")
    private Integer stargazerCnt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "repo_uuid", nullable = false, unique = true, updatable = false)
    private UUID repoUuid;

    @PrePersist
    public void prePersist() {
        if (this.repoUuid == null) {
            this.repoUuid = UUID.randomUUID();
        }
    }

    @Builder
    public MyGithubRepository(
            GithubProfile profile,
            String username,
            String repoName,
            String description,
            String htmlUrl,
            String language,
            Integer stargazerCnt,
            OffsetDateTime updatedAt,
            OffsetDateTime createdAt
    ) {
        this.profile = profile;
        this.username = username;
        this.repoName = repoName;
        this.description = description;
        this.htmlUrl = htmlUrl;
        this.language = language;
        this.stargazerCnt = stargazerCnt;
        this.updatedAt = updatedAt;
        this.createdAt = createdAt;
    }

    public void updateFromGithub(
            String repoName,
            String description,
            String htmlUrl,
            String language,
            Integer stargazerCnt,
            OffsetDateTime updatedAt
    ) {
        this.repoName = repoName;
        this.description = description;
        this.htmlUrl = htmlUrl;
        this.language = language;
        this.stargazerCnt = stargazerCnt;
        this.updatedAt = updatedAt;
    }
}
