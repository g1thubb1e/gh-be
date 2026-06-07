package com.ssafy.githubble.domain.ai.service;

import com.ssafy.githubble.domain.ai.dto.GraphPayload;
import com.ssafy.githubble.domain.ai.dto.request.IngestRequest;
import com.ssafy.githubble.global.code.ErrorCode;
import com.ssafy.githubble.global.exception.BusinessException;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class IngestionService {

    private final Driver driver;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final IngestionLockRegistry lockRegistry;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public IngestionService(Driver driver,
                            EmbeddingModel embeddingModel,
                            EmbeddingStore<TextSegment> embeddingStore,
                            IngestionLockRegistry lockRegistry,
                            TransactionTemplate transactionTemplate) {
        this.driver = driver;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.lockRegistry = lockRegistry;
        this.transactionTemplate = transactionTemplate;
    }

    public Map<String, Object> ingest(IngestRequest req) {
        String archId = buildArchId(req.username(), req.repo());
        lockRegistry.acquire(archId);
        try {
            // JPA TX: 기존 임베딩 삭제
            embeddingStore.removeAll(MetadataFilterBuilder.metadataKey("archId").isEqualTo(archId));

            // JPA TX: 신규 임베딩 적재
            List<Embedding> embeddings = persistEmbeddings(archId, req);

            // Neo4j TX: 그래프 순수 적재
            transactionTemplate.execute(status -> {
                purgeGraphOnly(archId);
                persistGraph(archId, req, embeddings);
                precomputeDegrees(archId);
                return null;
            });

            long groupCount = countLabel(archId, "CONTAINS_GROUP");
            long nodeCount = countLabel(archId, "CONTAINS_NODE");
            long edgeCount = countLabel(archId, "CONTAINS_EDGE");
            log.info("Ingestion complete: archId={}, groups={}, nodes={}, edges={}",
                    archId, groupCount, nodeCount, edgeCount);
            return Map.of(
                    "archId", archId,
                    "groupCount", groupCount,
                    "nodeCount", nodeCount,
                    "edgeCount", edgeCount
            );
        } catch (Exception e) {
            log.warn("Ingestion failed for archId={}, attempting embedding compensation", archId);
            try {
                embeddingStore.removeAll(MetadataFilterBuilder.metadataKey("archId").isEqualTo(archId));
            } catch (Exception ce) {
                log.warn("ai.ingestion.event event=compensation_remove_all_failed archId={} errorType={}",
                        archId, ce.getClass().getName());
                e.addSuppressed(ce);
            }
            throw new BusinessException(ErrorCode.FAIL, "Ingestion failed for archId=" + archId);
        } finally {
            lockRegistry.release(archId);
        }
    }

    public void delete(String archId) {
        lockRegistry.acquire(archId);
        try {
            // JPA TX: 임베딩 먼저 삭제 (멱등 — 없어도 no-op)
            embeddingStore.removeAll(MetadataFilterBuilder.metadataKey("archId").isEqualTo(archId));

            // Neo4j TX: 그래프 삭제
            transactionTemplate.execute(status -> {
                purgeGraphOnly(archId);
                return null;
            });
            log.info("Deleted archId={}", archId);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.FAIL, "Delete failed for archId=" + archId);
        } finally {
            lockRegistry.release(archId);
        }
    }

    private void purgeGraphOnly(String archId) {
        try (Session session = driver.session()) {
            // Step i: EdgeText 정리
            session.run("""
                MATCH (a:Architecture {id:$archId})-[:CONTAINS_EDGE]->(et:EdgeText)
                DETACH DELETE et
                """, Map.of("archId", archId));

            // Step ii: 그래프 정리
            session.run("""
                MATCH (a:Architecture {id:$archId})
                OPTIONAL MATCH (a)-[:CONTAINS_NODE|CONTAINS_GROUP]->(x)
                DETACH DELETE a, x
                """, Map.of("archId", archId));
        }
    }

    private void persistGraph(String archId, IngestRequest req, List<Embedding> embeddings) {
        List<GraphPayload.GroupDto> groups = req.graph().getGroups();
        List<GraphPayload.NodeDto> nodes = req.graph().getNodes();
        List<GraphPayload.EdgeDto> edges = req.graph().getEdges();
        boolean hasEmbeddings = !embeddings.isEmpty()
                && embeddings.size() == groups.size() + nodes.size() + edges.size();

        String groupQuery = "MATCH (a:Architecture {id:$archId})\n"
                + "MERGE (g:Group {compositeId:$compositeId})\n"
                + "SET g.archId=$archId, g.groupId=$groupId, g.label=$label, g.description=$desc"
                + (hasEmbeddings ? ", g.embedding=$embedding" : "")
                + "\nMERGE (a)-[:CONTAINS_GROUP]->(g)";

        String nodeQuery = "MATCH (a:Architecture {id:$archId})\n"
                + "MERGE (n:Node {compositeId:$compositeId})\n"
                + "SET n.archId=$archId, n.nodeId=$nodeId, n.label=$label, n.type=$type,\n"
                + "    n.description=$desc, n.path=$path, n.shape=$shape,\n"
                + "    n.missingRef=false, n.degree=0"
                + (hasEmbeddings ? ", n.embedding=$embedding" : "")
                + "\nMERGE (a)-[:CONTAINS_NODE]->(n)";

        String edgeTextQuery = "MATCH (a:Architecture {id:$archId})\n"
                + "MATCH (from:Node {compositeId:$fromId})\n"
                + "MATCH (to:Node {compositeId:$toId})\n"
                + "MERGE (et:EdgeText {compositeId:$etId})\n"
                + "SET et.archId=$archId, et.fromNodeId=$fromNodeId, et.toNodeId=$toNodeId,\n"
                + "    et.label=$label, et.description=$desc, et.style=$style"
                + (hasEmbeddings ? ", et.embedding=$embedding" : "")
                + "\nMERGE (et)-[:DESCRIBES_EDGE]->(from)\nMERGE (et)-[:DESCRIBES_EDGE]->(to)"
                + "\nMERGE (a)-[:CONTAINS_EDGE]->(et)";

        try (Session session = driver.session()) {
            // Architecture MERGE
            session.run("""
                MERGE (a:Architecture {id:$archId})
                SET a.username=$username, a.repo=$repo,
                    a.lastAttempt=$attempt, a.rawText=$rawText,
                    a.ingestedAt=timestamp()
                """, Map.of(
                    "archId", archId,
                    "username", req.username(),
                    "repo", req.repo(),
                    "attempt", req.attempt() != null ? req.attempt() : "",
                    "rawText", req.graph().getRawText() != null ? req.graph().getRawText() : ""
            ));

            // Groups MERGE
            for (int i = 0; i < groups.size(); i++) {
                GraphPayload.GroupDto g = groups.get(i);
                String compositeId = archId + ":" + g.getId();
                Map<String, Object> params = new HashMap<>(Map.of(
                        "archId", archId,
                        "compositeId", compositeId,
                        "groupId", g.getId() != null ? g.getId() : "",
                        "label", g.getLabel() != null ? g.getLabel() : "",
                        "desc", g.getDescription() != null ? g.getDescription() : ""
                ));
                if (hasEmbeddings) params.put("embedding", toFloatList(embeddings.get(i).vector()));
                session.run(groupQuery, params);
            }

            // Nodes MERGE
            for (int i = 0; i < nodes.size(); i++) {
                GraphPayload.NodeDto n = nodes.get(i);
                String compositeId = archId + ":" + n.getId();
                Map<String, Object> params = new HashMap<>(Map.of(
                        "archId", archId,
                        "compositeId", compositeId,
                        "nodeId", n.getId() != null ? n.getId() : "",
                        "label", n.getLabel() != null ? n.getLabel() : "",
                        "type", n.getType() != null ? n.getType() : "",
                        "desc", n.getDescription() != null ? n.getDescription() : "",
                        "path", n.getPath() != null ? n.getPath() : "",
                        "shape", n.getShape() != null ? n.getShape() : ""
                ));
                if (hasEmbeddings) params.put("embedding", toFloatList(embeddings.get(groups.size() + i).vector()));
                session.run(nodeQuery, params);

                if (n.getGroupId() != null && !n.getGroupId().isBlank()) {
                    String groupCompositeId = archId + ":" + n.getGroupId().trim();
                    session.run("""
                        MATCH (n:Node {compositeId:$nodeCompositeId})
                        MATCH (g:Group {compositeId:$groupCompositeId})
                        MERGE (n)-[:BELONGS_TO]->(g)
                        """, Map.of(
                            "nodeCompositeId", compositeId,
                            "groupCompositeId", groupCompositeId
                    ));
                } else {
                    log.warn("Node {} has no groupId — orphan node", n.getId());
                }
            }

            // Edges MERGE
            for (int i = 0; i < edges.size(); i++) {
                GraphPayload.EdgeDto e = edges.get(i);
                String edgeId = sha1(archId + e.getFrom() + e.getTo() + e.getLabel());
                String fromCompositeId = archId + ":" + e.getFrom();
                String toCompositeId = archId + ":" + e.getTo();
                String edgeCompositeId = archId + ":" + edgeId;

                session.run("""
                    MATCH (from:Node {compositeId:$fromId})
                    MATCH (to:Node {compositeId:$toId})
                    MERGE (from)-[r:CONNECTS_TO {edgeId:$edgeId}]->(to)
                    SET r.label=$label, r.description=$desc, r.style=$style
                    """, Map.of(
                        "fromId", fromCompositeId,
                        "toId", toCompositeId,
                        "edgeId", edgeId,
                        "label", e.getLabel() != null ? e.getLabel() : "",
                        "desc", e.getDescription() != null ? e.getDescription() : "",
                        "style", e.getStyle() != null ? e.getStyle() : ""
                ));

                Map<String, Object> etParams = new HashMap<>(Map.of(
                        "archId", archId,
                        "fromId", fromCompositeId,
                        "toId", toCompositeId,
                        "etId", edgeCompositeId,
                        "fromNodeId", e.getFrom() != null ? e.getFrom() : "",
                        "toNodeId", e.getTo() != null ? e.getTo() : "",
                        "label", e.getLabel() != null ? e.getLabel() : "",
                        "desc", e.getDescription() != null ? e.getDescription() : "",
                        "style", e.getStyle() != null ? e.getStyle() : ""
                ));
                if (hasEmbeddings) etParams.put("embedding", toFloatList(embeddings.get(groups.size() + nodes.size() + i).vector()));
                session.run(edgeTextQuery, etParams);
            }
        }
    }

    private List<Float> toFloatList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float f : arr) list.add(f);
        return list;
    }

    private void precomputeDegrees(String archId) {
        try (Session session = driver.session()) {
            session.run("""
                MATCH (a:Architecture {id:$archId})-[:CONTAINS_NODE]->(n:Node)
                OPTIONAL MATCH (n)-[r:CONNECTS_TO]-()
                WITH n, count(r) AS d
                SET n.degree = d
                """, Map.of("archId", archId));
        }
    }

    private List<Embedding> persistEmbeddings(String archId, IngestRequest req) {
        List<TextSegment> segments = new ArrayList<>();

        for (GraphPayload.GroupDto g : req.graph().getGroups()) {
            String refId = archId + ":" + g.getId();
            String text = "[GROUP] " + nullSafe(g.getLabel()) + "\n" + nullSafe(g.getDescription());
            segments.add(TextSegment.from(text, Metadata.from(Map.of(
                    "archId", archId, "kind", "group", "refId", refId, "label", nullSafe(g.getLabel())
            ))));
        }

        for (GraphPayload.NodeDto n : req.graph().getNodes()) {
            String refId = archId + ":" + n.getId();
            String text = "[NODE] " + nullSafe(n.getLabel()) + " (" + nullSafe(n.getType()) + ")\n"
                    + nullSafe(n.getPath()) + "\n" + nullSafe(n.getDescription());
            Map<String, Object> nodeMeta = new HashMap<>(Map.of(
                    "archId",      archId,
                    "kind",        "node",
                    "refId",       refId,
                    "label",       nullSafe(n.getLabel()),
                    "nodeId",      nullSafe(n.getId()),
                    "nodeType",    nullSafe(n.getType()),
                    "description", nullSafe(n.getDescription()),
                    "path",        nullSafe(n.getPath())
            ));
            segments.add(TextSegment.from(text, Metadata.from(nodeMeta)));
        }

        for (GraphPayload.EdgeDto e : req.graph().getEdges()) {
            String refId = sha1(archId + e.getFrom() + e.getTo() + e.getLabel());
            String fromLabel = resolveNodeLabel(req, e.getFrom());
            String toLabel = resolveNodeLabel(req, e.getTo());
            String text = "[EDGE] " + fromLabel + " -- " + nullSafe(e.getLabel()) + " --> " + toLabel
                    + "\n" + nullSafe(e.getDescription());
            Map<String, Object> edgeMeta = new HashMap<>(Map.of(
                    "archId",      archId,
                    "kind",        "edge",
                    "refId",       refId,
                    "label",       nullSafe(e.getLabel()),
                    "fromNodeId",  nullSafe(e.getFrom()),
                    "toNodeId",    nullSafe(e.getTo()),
                    "description", nullSafe(e.getDescription())
            ));
            segments.add(TextSegment.from(text, Metadata.from(edgeMeta)));
        }

        // 대표 파일 내용 임베딩 (선택)
        if (req.fileContents() != null && !req.fileContents().isEmpty()) {
            for (var fc : req.fileContents()) {
                String refId = archId + ":file:" + sha1(fc.path());
                String text = "[FILE] " + nullSafe(fc.label()) + " - " + nullSafe(fc.path()) + "\n"
                        + nullSafe(fc.content());
                Map<String, Object> fileMeta = new HashMap<>(Map.of(
                        "archId", archId,
                        "kind", "file",
                        "refId", refId,
                        "label", nullSafe(fc.label()),
                        "path", nullSafe(fc.path())
                ));
                segments.add(TextSegment.from(text, Metadata.from(fileMeta)));
            }
            log.info("추가된 대표 파일 임베딩: archId={}, count={}", archId, req.fileContents().size());
        }

        if (!segments.isEmpty()) {
            log.info("Embedding 생성 시작: archId={}, segments={}(groups={}, nodes={}, edges={})",
                    archId, segments.size(),
                    req.graph().getGroups().size(),
                    req.graph().getNodes().size(),
                    req.graph().getEdges().size());
            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
            embeddingStore.addAll(embeddings, segments);
            log.info("Embedding 적재 완료: archId={}, count={}", archId, segments.size());
            return embeddings;
        }

        return List.of();
    }

    private String resolveNodeLabel(IngestRequest req, String nodeId) {
        if (nodeId == null) return "";
        return req.graph().getNodes().stream()
                .filter(n -> nodeId.equals(n.getId()))
                .map(n -> n.getLabel() != null ? n.getLabel() : nodeId)
                .findFirst()
                .orElse(nodeId);
    }

    private long countLabel(String archId, String relType) {
        try (Session session = driver.session()) {
            return session.run(
                    "MATCH (a:Architecture {id:$archId})-[:" + relType + "]->(x) RETURN count(x)",
                    Map.of("archId", archId)
            ).single().get(0).asLong();
        }
    }

    public static String buildArchId(String username, String repo) {
        return username + "__" + repo;
    }

    private String nullSafe(String s) {
        return s != null ? s : "";
    }

    private String sha1(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] hash = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
