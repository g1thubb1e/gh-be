package com.ssafy.githubble.domain.profile.repository;

import com.ssafy.githubble.domain.profile.domain.GithubProfile;
import com.ssafy.githubble.domain.profile.domain.MyGithubRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MyGithubRepositoryRepository extends JpaRepository<MyGithubRepository, Long> {
    Optional<MyGithubRepository> findByProfileAndRepoName(GithubProfile profile, String repoName);
    Page<MyGithubRepository> findByProfile(GithubProfile profile, Pageable pageable);
}

