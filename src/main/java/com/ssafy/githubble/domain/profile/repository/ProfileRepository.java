package com.ssafy.githubble.domain.profile.repository;

import com.ssafy.githubble.domain.profile.domain.GithubProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProfileRepository extends JpaRepository<GithubProfile, Long> {

    Optional<GithubProfile> findByUsername(String username);
    Optional<GithubProfile> findByAppuserId(Long appuserId);

}
