package com.ssafy.githubble.domain.profile.repository;

import com.ssafy.githubble.domain.profile.domain.TechStack;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TechStackRepository extends JpaRepository<TechStack, Long> {
    List<TechStack> findAllByOrderByNameAsc();
    List<TechStack> findAllByTechstackUuidIn(Collection<UUID> techstackUuids);
    List<TechStack> findAllByNameIn(Collection<String> names);
}
