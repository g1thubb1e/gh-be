package com.ssafy.githubble.neo4j.repository;

import com.ssafy.githubble.neo4j.entity.NodeEntity;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface NodeRepository extends Neo4jRepository<NodeEntity, String> {
}
