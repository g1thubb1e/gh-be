package com.ssafy.githubble.domain.ai.repository;

import com.ssafy.githubble.domain.ai.entity.EmbeddingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EmbeddingRepository extends JpaRepository<EmbeddingEntity, UUID> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from EmbeddingEntity e where e.archId = :archId")
    int deleteByArchId(@Param("archId") String archId);

    @Query(value = """
        SELECT id              AS id,
               arch_id         AS archId,
               kind            AS kind,
               ref_id          AS refId,
               label           AS label,
               content         AS content,
               node_id         AS nodeId,
               node_type       AS nodeType,
               description     AS description,
               path            AS path,
               from_node_id    AS fromNodeId,
               to_node_id      AS toNodeId,
               1 - (embedding <=> CAST(:queryVec AS vector)) AS score
          FROM graphrag_embeddings
         WHERE arch_id = :archId
           AND kind    = :kind
         ORDER BY embedding <=> CAST(:queryVec AS vector)
         LIMIT :topK
        """, nativeQuery = true)
    List<EmbeddingSearchRow> searchByCosine(@Param("archId") String archId,
                                             @Param("kind") String kind,
                                             @Param("queryVec") String queryVec,
                                             @Param("topK") int topK);

    interface EmbeddingSearchRow {
        UUID getId();
        String getArchId();
        String getKind();
        String getRefId();
        String getLabel();
        String getContent();
        double getScore();
        String getNodeId();
        String getNodeType();
        String getDescription();
        String getPath();
        String getFromNodeId();
        String getToNodeId();
    }
}
