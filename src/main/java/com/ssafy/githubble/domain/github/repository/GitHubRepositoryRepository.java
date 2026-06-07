package com.ssafy.githubble.domain.github.repository;

import com.ssafy.githubble.domain.github.domain.GitHubRepositoryEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface GitHubRepositoryRepository extends JpaRepository<GitHubRepositoryEntity, Long> {

    Optional<GitHubRepositoryEntity> findByGithubRepoId(Long githubRepoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM GitHubRepositoryEntity e WHERE e.githubRepoId = :githubRepoId")
    Optional<GitHubRepositoryEntity> findByGithubRepoIdWithLock(Long githubRepoId);

    Optional<GitHubRepositoryEntity> findByRepoUuid(UUID repoUuid);

    Optional<GitHubRepositoryEntity> findByOwnerLoginIgnoreCaseAndRepoNameIgnoreCase(String ownerLogin, String repoName);
}
