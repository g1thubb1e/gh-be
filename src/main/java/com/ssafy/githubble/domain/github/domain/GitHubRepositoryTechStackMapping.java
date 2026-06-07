package com.ssafy.githubble.domain.github.domain;

import com.ssafy.githubble.domain.profile.domain.TechStack;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "github_repository_tech_stack_mapping")
@Getter
@NoArgsConstructor
public class GitHubRepositoryTechStackMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "repo_techstack_mapping_id")
    private Long repoTechStackMappingId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repo_id", nullable = false)
    private GitHubRepositoryEntity repository;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "techstack_id", nullable = false)
    private TechStack techStack;

    @Builder
    public GitHubRepositoryTechStackMapping(GitHubRepositoryEntity repository, TechStack techStack) {
        this.repository = repository;
        this.techStack = techStack;
    }
}
