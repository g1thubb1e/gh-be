package com.ssafy.githubble.domain.profile.repository;

import com.ssafy.githubble.domain.profile.domain.GithubProfile;
import com.ssafy.githubble.domain.profile.domain.TechStackMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TechStackMappingRepository extends JpaRepository<TechStackMapping, Long> {

    void deleteByProfile(GithubProfile profile);

    List<TechStackMapping> findAllByProfile(GithubProfile profile);
}
