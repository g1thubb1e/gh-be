package com.ssafy.githubble.neo4j.repository;

import com.ssafy.githubble.neo4j.entity.Architecture;
import org.springframework.data.neo4j.repository.Neo4jRepository;

import java.util.Optional;

public interface ArchitectureRepository extends Neo4jRepository<Architecture, String> {

    Optional<Architecture> findByUsernameAndRepo(String username, String repo);
}
