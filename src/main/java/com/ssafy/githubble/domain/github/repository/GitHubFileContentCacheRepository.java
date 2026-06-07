package com.ssafy.githubble.domain.github.repository;

import com.ssafy.githubble.domain.github.domain.GitHubFileContentCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GitHubFileContentCacheRepository extends JpaRepository<GitHubFileContentCache, Long> {

    Optional<GitHubFileContentCache> findByRepositoryRepoIdAndRefAndPath(Long repoId, String ref, String path);

    @Query("""
            select coalesce(sum(c.size), 0)
            from GitHubFileContentCache c
            where c.repository.repoId = :repoId
              and c.contentAvailable = true
            """)
    long sumCachedContentSize(@Param("repoId") Long repoId);

    List<GitHubFileContentCache> findByRepositoryRepoIdAndContentAvailableTrueOrderByLastAccessedAtAsc(Long repoId);
}
