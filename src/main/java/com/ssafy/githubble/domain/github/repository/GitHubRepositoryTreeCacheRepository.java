package com.ssafy.githubble.domain.github.repository;

import com.ssafy.githubble.domain.github.domain.GitHubRepositoryTreeCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GitHubRepositoryTreeCacheRepository extends JpaRepository<GitHubRepositoryTreeCache, Long> {

    Optional<GitHubRepositoryTreeCache> findByRepositoryRepoIdAndRef(Long repoId, String ref);
}
