package com.ssafy.githubble.domain.github.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "github_file_content_cache",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_github_file_content_cache_repo_ref_path", columnNames = {"repo_id", "ref", "path"})
        }
)
@Getter
@NoArgsConstructor
public class GitHubFileContentCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "file_content_cache_id")
    private Long fileContentCacheId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repo_id", nullable = false)
    private GitHubRepositoryEntity repository;

    @Column(name = "ref", nullable = false, length = 255)
    private String ref;

    @Column(name = "path", nullable = false, columnDefinition = "text")
    private String path;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "sha", nullable = false, length = 64)
    private String sha;

    @Column(name = "type", nullable = false, length = 30)
    private String type;

    @Column(name = "size")
    private Long size;

    @Column(name = "encoding", length = 50)
    private String encoding;

    @Column(name = "content", columnDefinition = "text")
    private String content;

    @Column(name = "content_available", nullable = false)
    private Boolean contentAvailable;

    @Column(name = "is_binary", nullable = false)
    private Boolean binary;

    @Column(name = "unavailable_reason", length = 50)
    private String unavailableReason;

    @Column(name = "html_url", columnDefinition = "text")
    private String htmlUrl;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    @Column(name = "last_accessed_at", nullable = false)
    private LocalDateTime lastAccessedAt;

    public GitHubFileContentCache(GitHubRepositoryEntity repository, String ref, String path, String fileName, String sha,
                                  String type, Long size, String encoding, String content, Boolean contentAvailable,
                                  Boolean binary, String unavailableReason, String htmlUrl) {
        this.repository = repository;
        this.ref = ref;
        this.path = path;
        this.fileName = fileName;
        this.sha = sha;
        this.type = type;
        this.size = size;
        this.encoding = encoding;
        this.content = content;
        this.contentAvailable = contentAvailable;
        this.binary = binary;
        this.unavailableReason = unavailableReason;
        this.htmlUrl = htmlUrl;
        LocalDateTime now = LocalDateTime.now();
        this.fetchedAt = now;
        this.lastAccessedAt = now;
    }

    public void refresh(GitHubFileContentCache source) {
        this.fileName = source.fileName;
        this.sha = source.sha;
        this.type = source.type;
        this.size = source.size;
        this.encoding = source.encoding;
        this.content = source.content;
        this.contentAvailable = source.contentAvailable;
        this.binary = source.binary;
        this.unavailableReason = source.unavailableReason;
        this.htmlUrl = source.htmlUrl;
        LocalDateTime now = LocalDateTime.now();
        this.fetchedAt = now;
        this.lastAccessedAt = now;
    }

    public void touch() {
        this.lastAccessedAt = LocalDateTime.now();
    }

    public void markValidatedUnchanged() {
        LocalDateTime now = LocalDateTime.now();
        this.fetchedAt = now;
        this.lastAccessedAt = now;
    }
}
