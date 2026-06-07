package com.ssafy.githubble.domain.auth.repository;

import com.ssafy.githubble.domain.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByGithubId(Long githubId);

    Optional<User> findByUsernameAndIsDeletedFalse(String username);

}
