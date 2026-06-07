package com.ssafy.githubble.domain.github.repository;

import com.ssafy.githubble.domain.github.domain.GitHubRepositorySummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GitHubRepositorySummaryRepository extends JpaRepository<GitHubRepositorySummary, Long> {

    Optional<GitHubRepositorySummary> findByRepositoryRepoId(Long repoId);

    Optional<GitHubRepositorySummary> findFirstByRepositoryRepoIdOrderByCreatedAtDesc(Long repoId);
}
