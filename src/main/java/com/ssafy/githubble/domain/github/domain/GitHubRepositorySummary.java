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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "github_repository_summary")
@Getter
@NoArgsConstructor
public class GitHubRepositorySummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "diagram_id")
    private Long diagramId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repo_id", nullable = false)
    private GitHubRepositoryEntity repository;

    @Column(name = "explanation", columnDefinition = "text")
    private String explanation;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Builder
    public GitHubRepositorySummary(GitHubRepositoryEntity repository, String explanation) {
        this.repository = repository;
        this.explanation = explanation;
    }

    public void updateExplanation(String explanation) {
        this.explanation = explanation;
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
    }
}
