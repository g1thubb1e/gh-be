package com.ssafy.githubble.domain.github.repository;

import com.ssafy.githubble.domain.github.domain.GitHubRepositoryParseCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GitHubRepositoryParseCacheRepository extends JpaRepository<GitHubRepositoryParseCache, Long> {

    Optional<GitHubRepositoryParseCache> findByRepositoryRepoId(Long repoId);

    void deleteByRepositoryRepoId(Long repoId);
}
