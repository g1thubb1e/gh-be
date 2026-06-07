package com.ssafy.githubble.domain.github.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "github_repository")
@Getter
@NoArgsConstructor
public class GitHubRepositoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "repo_id")
    private Long repoId;

    @Column(name = "github_repo_id", unique = true)
    private Long githubRepoId;

    @Column(name = "owner_login", nullable = false, length = 255)
    private String ownerLogin;

    @Column(name = "repo_name", nullable = false, length = 255)
    private String repoName;

    @Column(name = "default_branch", length = 255)
    private String defaultBranch;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "is_private", nullable = false)
    private Boolean privateRepository;

    @Column(name = "html_url", columnDefinition = "text")
    private String htmlUrl;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "repo_uuid", nullable = false, unique = true)
    private UUID repoUuid;

    public GitHubRepositoryEntity(Long githubRepoId, String ownerLogin, String repoName, String defaultBranch,
                                  String description, Boolean privateRepository, String htmlUrl) {
        this.githubRepoId = githubRepoId;
        this.ownerLogin = ownerLogin;
        this.repoName = repoName;
        this.defaultBranch = defaultBranch;
        this.description = description;
        this.privateRepository = privateRepository;
        this.htmlUrl = htmlUrl;
    }

    public void update(String defaultBranch, String description, Boolean privateRepository, String htmlUrl) {
        this.defaultBranch = defaultBranch;
        this.description = description;
        this.privateRepository = privateRepository;
        this.htmlUrl = htmlUrl;
        this.lastSyncedAt = LocalDateTime.now();
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.lastSyncedAt = now;
        if (this.repoUuid == null) {
            this.repoUuid = UUID.randomUUID();
        }
        if (this.privateRepository == null) {
            this.privateRepository = false;
        }
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
