package com.ssafy.githubble.domain.github.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "github_repository_parse_cache")
@Getter
@NoArgsConstructor
public class GitHubRepositoryParseCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "parse_cache_id")
    private Long parseCacheId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repo_id", nullable = false)
    private GitHubRepositoryEntity repository;

    @Column(name = "primary_language", length = 255)
    private String primaryLanguage;

    @Column(name = "stars", nullable = false)
    private Long stars;

    @Column(name = "forks", nullable = false)
    private Long forks;

    @Column(name = "open_issue_count", nullable = false)
    private Long openIssueCount;

    @Column(name = "open_pull_request_count", nullable = false)
    private Long openPullRequestCount;

    @Column(name = "open_issue_and_pr_count", nullable = false)
    private Long openIssueAndPullRequestCount;

    @Column(name = "languages_json", columnDefinition = "text")
    private String languagesJson;

    @Column(name = "readme_path", length = 1024)
    private String readmePath;

    @Column(name = "readme_content", columnDefinition = "text")
    private String readmeContent;

    @Column(name = "readme_encoding", length = 10)
    private String readmeEncoding;

    @Column(name = "warnings_json", columnDefinition = "text")
    private String warningsJson;

    @Column(name = "parsed_at", nullable = false)
    private LocalDateTime parsedAt;

    public GitHubRepositoryParseCache(
            GitHubRepositoryEntity repository,
            String primaryLanguage,
            Long stars,
            Long forks,
            Long openIssueCount,
            Long openPullRequestCount,
            Long openIssueAndPullRequestCount,
            String languagesJson,
            String readmePath,
            String readmeContent,
            String readmeEncoding,
            String warningsJson
    ) {
        this.repository = repository;
        this.primaryLanguage = primaryLanguage;
        this.stars = stars;
        this.forks = forks;
        this.openIssueCount = openIssueCount;
        this.openPullRequestCount = openPullRequestCount;
        this.openIssueAndPullRequestCount = openIssueAndPullRequestCount;
        this.languagesJson = languagesJson;
        this.readmePath = readmePath;
        this.readmeContent = readmeContent;
        this.readmeEncoding = readmeEncoding;
        this.warningsJson = warningsJson;
    }

    public void update(
            String primaryLanguage,
            Long stars,
            Long forks,
            Long openIssueCount,
            Long openPullRequestCount,
            Long openIssueAndPullRequestCount,
            String languagesJson,
            String readmePath,
            String readmeContent,
            String readmeEncoding,
            String warningsJson
    ) {
        this.primaryLanguage = primaryLanguage;
        this.stars = stars;
        this.forks = forks;
        this.openIssueCount = openIssueCount;
        this.openPullRequestCount = openPullRequestCount;
        this.openIssueAndPullRequestCount = openIssueAndPullRequestCount;
        this.languagesJson = languagesJson;
        this.readmePath = readmePath;
        this.readmeContent = readmeContent;
        this.readmeEncoding = readmeEncoding;
        this.warningsJson = warningsJson;
        this.parsedAt = LocalDateTime.now();
    }

    @PrePersist
    void prePersist() {
        if (this.parsedAt == null) {
            this.parsedAt = LocalDateTime.now();
        }
        if (this.stars == null) this.stars = 0L;
        if (this.forks == null) this.forks = 0L;
        if (this.openIssueCount == null) this.openIssueCount = 0L;
        if (this.openPullRequestCount == null) this.openPullRequestCount = 0L;
        if (this.openIssueAndPullRequestCount == null) this.openIssueAndPullRequestCount = 0L;
    }
}
