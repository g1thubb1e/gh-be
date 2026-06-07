package com.ssafy.githubble.neo4j.repository;

import com.ssafy.githubble.neo4j.entity.Architecture;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GraphQueryRepository extends Neo4jRepository<Architecture, String> {

    @Query("""
        MATCH (a:Architecture)
        RETURN a
        ORDER BY a.ingestedAt DESC
        SKIP $skip LIMIT $limit
        """)
    List<Architecture> findAllPaged(@Param("skip") long skip, @Param("limit") int limit);

    @Query("MATCH (a:Architecture) RETURN count(a)")
    long countAll();

    @Query("""
        MATCH (a:Architecture {id: $archId})-[:CONTAINS_GROUP]->(g:Group)
        RETURN count(DISTINCT g)
        """)
    long countGroups(@Param("archId") String archId);

    @Query("""
        MATCH (a:Architecture {id: $archId})-[:CONTAINS_NODE]->(n:Node)
        RETURN count(DISTINCT n)
        """)
    long countNodes(@Param("archId") String archId);

    @Query("""
        MATCH (a:Architecture {id: $archId})-[:CONTAINS_NODE]->(n)-[r:CONNECTS_TO]->()
        RETURN count(r) AS edgeCount
        """)
    long countEdges(@Param("archId") String archId);
}
