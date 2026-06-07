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
        name = "github_repository_tree_cache",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_github_repository_tree_cache_repo_ref", columnNames = {"repo_id", "ref"})
        }
)
@Getter
@NoArgsConstructor
public class GitHubRepositoryTreeCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tree_cache_id")
    private Long treeCacheId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repo_id", nullable = false)
    private GitHubRepositoryEntity repository;

    @Column(name = "ref", nullable = false, length = 255)
    private String ref;

    @Column(name = "tree_sha", length = 64)
    private String treeSha;

    @Column(name = "truncated", nullable = false)
    private Boolean truncated;

    @Column(name = "tree_json", nullable = false, columnDefinition = "text")
    private String treeJson;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    public GitHubRepositoryTreeCache(GitHubRepositoryEntity repository, String ref, String treeSha,
                                     Boolean truncated, String treeJson) {
        this.repository = repository;
        this.ref = ref;
        this.treeSha = treeSha;
        this.truncated = truncated;
        this.treeJson = treeJson;
        this.fetchedAt = LocalDateTime.now();
    }

    public void refresh(String treeSha, Boolean truncated, String treeJson) {
        this.treeSha = treeSha;
        this.truncated = truncated;
        this.treeJson = treeJson;
        this.fetchedAt = LocalDateTime.now();
    }
}
