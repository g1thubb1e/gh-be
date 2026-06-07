package com.ssafy.githubble.neo4j.repository;

import com.ssafy.githubble.neo4j.entity.Group;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface GroupRepository extends Neo4jRepository<Group, String> {
}
