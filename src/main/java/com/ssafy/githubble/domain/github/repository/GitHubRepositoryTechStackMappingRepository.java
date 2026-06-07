package com.ssafy.githubble.domain.github.repository;

import com.ssafy.githubble.domain.github.domain.GitHubRepositoryTechStackMapping;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GitHubRepositoryTechStackMappingRepository extends JpaRepository<GitHubRepositoryTechStackMapping, Long> {

    @EntityGraph(attributePaths = "techStack")
    List<GitHubRepositoryTechStackMapping> findByRepositoryRepoId(Long repoId);

    void deleteByRepositoryRepoId(Long repoId);
}
