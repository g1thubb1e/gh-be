package com.ssafy.githubble.domain.ai.component.retrieve;

import com.ssafy.githubble.domain.ai.component.QueryIntent;
import com.ssafy.githubble.global.util.LogSanitizer;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class GraphRetriever {

    private final Driver driver;
    private final LuceneQuerySanitizer sanitizer;

    private final EmbeddingModel embeddingModel;

    public List<GraphResult> graphRetrieve(QueryIntent intent, String archId, String query) {
        List<GraphResult> results = switch (intent) {
            case GROUP_DESCRIBE  -> groupSearch(archId, query);
            case ARCH_COUNTS     -> archCounts(archId);
            case GROUP_NODE_COUNT-> groupNodeCount(archId, query);
            case NODE_DESCRIBE   -> graphSearch(archId, query, 3, 2, 0.65);
            case EDGE_DESCRIBE   -> edgeSearch(archId, query, 3, 0.65);
            case NODE_LIST       -> nodeList(archId);
            case HUB_NODES       -> findHubNodes(archId, 3);
            case NODE_FALLBACK   -> graphSearch(archId, query, 6, 1, 0.2);
            default -> {
                log.warn("Unknown intent: {}", intent);
                yield Collections.emptyList();
            }
        };

        if (results.size() <= 2 && intent != QueryIntent.NODE_FALLBACK) {
            log.info("결과 부족({}개) → NODE_FALLBACK 재검색: intent={}", results.size(), intent);
            return graphSearch(archId, query, 6, 1, 0.2);
        }

        return results;
    }

    public List<GraphResult> nodeList(String archId) {
        String cypher =
                "MATCH (arch:Architecture {id: $archId})-[:CONTAINS_NODE]->(n:Node) " +
                "RETURN n.nodeId                      AS nodeId, " +
                "       coalesce(n.type, '')          AS nodeType, " +
                "       coalesce(n.label, '')         AS label, " +
                "       coalesce(n.description, '')   AS description, " +
                "       coalesce(n.degree, 0)         AS degree, " +
                "       coalesce(n.path, '')          AS path " +
                "ORDER BY degree DESC LIMIT 15";

        List<GraphResult> results = new ArrayList<>();
        try (Session session = driver.session()) {
            var result = session.run(cypher, Map.of("archId", archId));
            while (result.hasNext()) {
                var r = result.next();
                results.add(GraphResult.NodeResult.of(
                        r.get("nodeId").asString(),
                        r.get("nodeType").asString(""),
                        r.get("label").asString(""),
                        r.get("description").asString(""),
                        r.get("degree").asDouble(0),
                        r.get("path").asString(""),
                        0.0
                ));
            }
        }
        log.info("NodeList: archId={}, nodes={}", archId, results.size());
        return results;
    }

    public List<GraphResult> graphSearch(String archId, String query, int topK, int graphHops, double minScore) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        List<Float> queryVector = toFloatList(queryEmbedding.vector());

        List<GraphResult> results = new ArrayList<>();
        List<String> topCompositeIds = new ArrayList<>();
        Map<String, Double> scoreByCompositeId = new HashMap<>();

        String nodeCypher =
                "MATCH (arch:Architecture {id: $archId})-[:CONTAINS_NODE]->(seed:Node) " +
                "WHERE seed.embedding IS NOT NULL " +
                "WITH seed, vector.similarity.cosine(seed.embedding, $queryVector) AS seedScore " +
                "WHERE seedScore >= $minScore " +
                "ORDER BY seedScore DESC LIMIT $topK " +
                "MATCH p = (seed)-[:CONNECTS_TO*0.." + graphHops + "]-(reachable:Node) " +
                "WHERE reachable.embedding IS NOT NULL " +
                "  AND ALL(n IN nodes(p) WHERE n.embedding IS NOT NULL " +
                "       AND vector.similarity.cosine(n.embedding, $queryVector) >= $minScore) " +
                "WITH DISTINCT reachable, " +
                "     vector.similarity.cosine(reachable.embedding, $queryVector) AS score " +
                "WHERE score >= $minScore " +
                "RETURN reachable.compositeId               AS compositeId, " +
                "       reachable.nodeId                    AS nodeId, " +
                "       coalesce(reachable.type, '')        AS nodeType, " +
                "       coalesce(reachable.label, '')       AS label, " +
                "       coalesce(reachable.description, '') AS description, " +
                "       coalesce(reachable.degree, 0)       AS degree, " +
                "       coalesce(reachable.path, '')        AS path, " +
                "       score " +
                "ORDER BY score DESC " +
                "LIMIT 10";

        String edgeCypher =
                "MATCH (n:Node)-[r:CONNECTS_TO]->(m:Node) " +
                "WHERE n.compositeId IN $ids OR m.compositeId IN $ids " +
                "RETURN r.edgeId                    AS edgeId, " +
                "       coalesce(r.label, '')       AS label, " +
                "       coalesce(r.description, '') AS description, " +
                "       n.compositeId AS fromCompositeId, " +
                "       m.compositeId AS toCompositeId, " +
                "       n.nodeId      AS fromId, " +
                "       m.nodeId      AS toId";

        try (Session session = driver.session()) {
            var nodeResult = session.run(nodeCypher, Map.of(
                    "topK", topK,
                    "queryVector", queryVector,
                    "archId", archId,
                    "minScore", minScore
            ));
            while (nodeResult.hasNext()) {
                var r = nodeResult.next();
                String compositeId = r.get("compositeId").asString();
                double score = r.get("score").asDouble();
                results.add(GraphResult.NodeResult.of(
                        r.get("nodeId").asString(),
                        r.get("nodeType").asString(""),
                        r.get("label").asString(""),
                        r.get("description").asString(""),
                        r.get("degree").asDouble(0),
                        r.get("path").asString(""),
                        score
                ));
                topCompositeIds.add(compositeId);
                scoreByCompositeId.put(compositeId, score);
            }

            if (!topCompositeIds.isEmpty()) {
                var edgeResult = session.run(edgeCypher, Map.of("ids", topCompositeIds));
                while (edgeResult.hasNext()) {
                    var r = edgeResult.next();
                    String fromCid = r.get("fromCompositeId").asString();
                    String toCid   = r.get("toCompositeId").asString();
                    double edgeScore = (scoreByCompositeId.getOrDefault(fromCid, 0.0)
                            + scoreByCompositeId.getOrDefault(toCid, 0.0)) / 2.0;
                    results.add(new GraphResult.EdgeResult(
                            r.get("edgeId").asString(),
                            r.get("fromId").asString(),
                            r.get("label").asString(""),
                            r.get("toId").asString(),
                            r.get("description").asString(""),
                            edgeScore
                    ));
                }
            }
        }
        log.info("GraphSearch: archId={}, nodes={}, total={}", archId, topCompositeIds.size(), results.size());
        logQueryDebug("GraphSearch", query);
        return results;
    }

    public List<GraphResult> edgeSearch(String archId, String query, int topK, double minScore) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        List<Float> queryVector = toFloatList(queryEmbedding.vector());

        List<GraphResult> results = new ArrayList<>();
        List<String> topCompositeIds = new ArrayList<>();
        Map<String, Double> scoreByCompositeId = new HashMap<>();

        String etCypher =
                "MATCH (arch:Architecture {id: $archId})-[:CONTAINS_EDGE]->(et:EdgeText) " +
                "WHERE et.embedding IS NOT NULL " +
                "MATCH (from:Node {archId: $archId, nodeId: et.fromNodeId})" +
                "     -[r:CONNECTS_TO]->" +
                "     (to:Node   {archId: $archId, nodeId: et.toNodeId}) " +
                "WITH et, r.edgeId AS edgeId, vector.similarity.cosine(et.embedding, $queryVector) AS score " +
                "WHERE score >= $minScore " +
                "RETURN edgeId AS edgeId, et.compositeId AS compositeId, et.fromNodeId AS fromNodeId, " +
                "       et.toNodeId AS toNodeId, et.label AS label, coalesce(et.description, '') AS desc, score " +
                "ORDER BY score DESC LIMIT $topK";

        String nodeCypher =
                "MATCH (et:EdgeText)-[:DESCRIBES_EDGE]->(n:Node) " +
                "WHERE et.compositeId IN $ids " +
                "RETURN n.nodeId                    AS nodeId, " +
                "       coalesce(n.type, '')        AS nodeType, " +
                "       coalesce(n.label, '')       AS label, " +
                "       coalesce(n.description, '') AS description, " +
                "       coalesce(n.degree, 0)       AS degree, " +
                "       coalesce(n.path, '')        AS path, " +
                "       et.compositeId              AS etCompositeId";

        try (Session session = driver.session()) {
            var etResult = session.run(etCypher, Map.of(
                    "topK", topK,
                    "queryVector", queryVector,
                    "archId", archId,
                    "minScore", minScore
            ));
            while (etResult.hasNext()) {
                var r = etResult.next();
                String compositeId = r.get("compositeId").asString();
                double score = r.get("score").asDouble();
                results.add(new GraphResult.EdgeResult(
                        r.get("edgeId").asString(),
                        r.get("fromNodeId").asString(),
                        r.get("label").asString(""),
                        r.get("toNodeId").asString(),
                        r.get("desc").asString(""),
                        score
                ));
                topCompositeIds.add(compositeId);
                scoreByCompositeId.put(compositeId, score);
            }

            if (!topCompositeIds.isEmpty()) {
                Map<String, Double> nodeMaxScore = new HashMap<>();
                Map<String, GraphResult.NodeResult> nodeResultMap = new LinkedHashMap<>();
                var nodeResult = session.run(nodeCypher, Map.of("ids", topCompositeIds));
                while (nodeResult.hasNext()) {
                    var r = nodeResult.next();
                    String nodeId = r.get("nodeId").asString();
                    double score = scoreByCompositeId.getOrDefault(r.get("etCompositeId").asString(), 0.0);
                    if (score > nodeMaxScore.getOrDefault(nodeId, -1.0)) {
                        nodeMaxScore.put(nodeId, score);
                        nodeResultMap.put(nodeId, GraphResult.NodeResult.of(
                                nodeId,
                                r.get("nodeType").asString(""),
                                r.get("label").asString(""),
                                r.get("description").asString(""),
                                r.get("degree").asDouble(0),
                                r.get("path").asString(""),
                                score
                        ));
                    }
                }
                results.addAll(nodeResultMap.values());
            }
        }
        log.info("EdgeSearch: archId={}, edges={}, total={}", archId, topCompositeIds.size(), results.size());
        logQueryDebug("EdgeSearch", query);
        return results;
    }

    public List<GraphResult> findHubNodes(String archId, int topK) {
        int aroundK = 3;
        final int maxEdges = (topK * aroundK) * 2;
        log.debug("findHubNodes: archId={}, topK={}", archId, topK);

        Map<String, String>  labelById     = new LinkedHashMap<>();
        Map<String, String>  descById      = new HashMap<>();
        Map<String, Double>  degreeById    = new HashMap<>();
        Map<String, String>  typeById      = new HashMap<>();
        Map<String, String>  pathById      = new HashMap<>();
        Map<String, String>  groupByNodeId = new HashMap<>();
        Map<String, Boolean> isHubById     = new HashMap<>();
        Set<String> groupIds  = new LinkedHashSet<>();
        Set<String> seenEdges = new LinkedHashSet<>();

        String nodeCypher =
            "MATCH (arch:Architecture {id: $archId})-[:CONTAINS_NODE]->(h:Node) " +
            "WITH h ORDER BY coalesce(h.degree, 0) DESC LIMIT $topK " +
            "WITH collect(h.nodeId) AS hubIds " +
            "UNWIND hubIds AS hubId " +
            "MATCH (hub:Node {nodeId: hubId, archId: $archId}) " +
            "CALL { " +
            "  WITH hub " +
            "  MATCH (hub)-[:CONNECTS_TO]-(nbr:Node) " +
            "  WHERE nbr.archId = hub.archId " +
            "  WITH nbr ORDER BY coalesce(nbr.degree, 0) DESC LIMIT $aroundK " +
            "  RETURN collect(nbr) AS topNbrs " +
            "} " +
            "WITH hubIds, [hub] + topNbrs AS bunch " +
            "UNWIND bunch AS n " +
            "WITH DISTINCT n, hubIds " +
            "OPTIONAL MATCH (n)-[:BELONGS_TO]->(g:Group) " +
            "RETURN n.nodeId                    AS nodeId, " +
            "       coalesce(n.type, '')        AS nodeType, " +
            "       coalesce(n.label, '')       AS label, " +
            "       coalesce(n.description, '') AS description, " +
            "       coalesce(n.degree, 0)       AS degree, " +
            "       coalesce(n.path, '')        AS path, " +
            "       g.groupId                   AS groupId, " +
            "       (n.nodeId IN hubIds)        AS isHub";

        List<GraphResult> results = new ArrayList<>();

        try (Session session = driver.session()) {
            var nodeResult = session.run(nodeCypher,
                    Map.of("archId", archId, "topK", topK, "aroundK", aroundK));
            while (nodeResult.hasNext()) {
                var r = nodeResult.next();
                String nodeId = r.get("nodeId").asString();
                if (!labelById.containsKey(nodeId)) {
                    labelById.put(nodeId, r.get("label").asString(""));
                    descById.put(nodeId, r.get("description").asString(""));
                    degreeById.put(nodeId, r.get("degree").asDouble(0));
                    typeById.put(nodeId, r.get("nodeType").asString(""));
                    pathById.put(nodeId, r.get("path").asString(""));
                    isHubById.put(nodeId, r.get("isHub").asBoolean(false));
                }
                if (!r.get("groupId").isNull()) {
                    String gid = r.get("groupId").asString();
                    groupByNodeId.putIfAbsent(nodeId, gid);
                    groupIds.add(gid);
                }
            }

            for (Map.Entry<String, String> entry : labelById.entrySet()) {
                String nodeId = entry.getKey();
                double degree = degreeById.getOrDefault(nodeId, 0.0);
                results.add(new GraphResult.NodeResult(
                        nodeId,
                        typeById.getOrDefault(nodeId, ""),
                        entry.getValue(),
                        descById.getOrDefault(nodeId, ""),
                        degree,
                        pathById.getOrDefault(nodeId, ""),
                        degree,
                        Boolean.TRUE.equals(isHubById.get(nodeId)),
                        groupByNodeId.get(nodeId)
                ));
            }

            if (!labelById.isEmpty()) {
                String edgeCypher =
                    "MATCH (n:Node {archId: $archId})-[r:CONNECTS_TO]->(m:Node) " +
                    "WHERE n.nodeId IN $nodeIds AND m.nodeId IN $nodeIds " +
                    "RETURN r.edgeId                    AS edgeId, " +
                    "       coalesce(r.label, '')       AS label, " +
                    "       coalesce(r.description, '') AS description, " +
                    "       n.nodeId AS fromId, m.nodeId AS toId " +
                    "LIMIT $maxEdges";

                var edgeResult = session.run(edgeCypher,
                        Map.of("archId", archId, "nodeIds", new ArrayList<>(labelById.keySet()),
                               "maxEdges", maxEdges));
                while (edgeResult.hasNext()) {
                    var r = edgeResult.next();
                    String edgeId = r.get("edgeId").asString();
                    if (seenEdges.add(edgeId)) {
                        results.add(new GraphResult.EdgeResult(
                                edgeId,
                                r.get("fromId").asString(),
                                r.get("label").asString(""),
                                r.get("toId").asString(),
                                r.get("description").asString(""),
                                0.0
                        ));
                    }
                }

                if (!groupIds.isEmpty()) {
                    String groupCypher =
                        "MATCH (g:Group) " +
                        "WHERE g.groupId IN $groupIds AND g.archId = $archId " +
                        "RETURN g.groupId                    AS groupId, " +
                        "       coalesce(g.label, '')       AS label, " +
                        "       coalesce(g.description, '') AS description";

                    var groupResult = session.run(groupCypher,
                            Map.of("groupIds", new ArrayList<>(groupIds), "archId", archId));
                    while (groupResult.hasNext()) {
                        var r = groupResult.next();
                        results.add(new GraphResult.GroupResult(
                                r.get("groupId").asString(),
                                r.get("label").asString(""),
                                r.get("description").asString("")
                        ));
                    }
                }
            }
        }

        log.info("findHubNodes: archId={}, nodes={}, edges={}, groups={}, total={}",
                archId, labelById.size(), seenEdges.size(), groupIds.size(), results.size());
        return results;
    }

    public List<GraphResult> archCounts(String archId) {
        List<GraphResult> results = new ArrayList<>();
        try (Session session = driver.session()) {
            String archCypher =
                "MATCH (arch:Architecture {id: $archId}) " +
                "RETURN COUNT { (arch)-[:CONTAINS_GROUP]->(:Group) } AS groupCnt, " +
                "       COUNT { (arch)-[:CONTAINS_NODE]->(:Node) }   AS nodeCnt";
            var archResult = session.run(archCypher, Map.of("archId", archId));
            if (archResult.hasNext()) {
                var r = archResult.next();
                results.add(GraphResult.CountResult.simple("arch_group_count", r.get("groupCnt").asLong(0)));
                results.add(GraphResult.CountResult.simple("arch_node_count",  r.get("nodeCnt").asLong(0)));
            }
        }
        log.info("counts: archId={}, results={}", archId, results.size());
        return results;
    }

    public List<GraphResult> groupNodeCount(String archId, String query) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        List<Float> queryVector = toFloatList(queryEmbedding.vector());

        String cypher =
            "MATCH (arch:Architecture {id: $archId})-[:CONTAINS_GROUP]->(g:Group) " +
            "WHERE g.embedding IS NOT NULL " +
            "WITH g, vector.similarity.cosine(g.embedding, $queryVector) AS score " +
            "ORDER BY score DESC LIMIT 1 " +
            "RETURN g.groupId                             AS groupId, " +
            "       coalesce(g.label, '')                AS label, " +
            "       COUNT { (g)<-[:BELONGS_TO]-(:Node) } AS cnt, " +
            "       score";

        try (Session session = driver.session()) {
            var result = session.run(cypher, Map.of("archId", archId, "queryVector", queryVector));
            if (result.hasNext()) {
                var r = result.next();
                String groupId = r.get("groupId").asString("");
                String label   = r.get("label").asString("");
                long   cnt     = r.get("cnt").asLong(0);
                double score   = r.get("score").asDouble(0);
                log.info("groupNodeCount: archId={}, groupId={}, cnt={}, score={}", archId, groupId, cnt, score);
                return List.of(new GraphResult.CountResult("group_node_count", cnt, groupId, label));
            }
        }
        log.info("groupNodeCount: archId={}, no matching group found", archId);
        return Collections.emptyList();
    }

    public List<GraphResult> groupSearch(String archId, String query) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        List<Float> queryVector = toFloatList(queryEmbedding.vector());

        String nodeCypher =
            "MATCH (arch:Architecture {id: $archId})-[:CONTAINS_GROUP]->(g:Group) " +
            "WHERE g.embedding IS NOT NULL " +
            "WITH g, vector.similarity.cosine(g.embedding, $queryVector) AS score " +
            "ORDER BY score DESC LIMIT 1 " +
            "MATCH (n:Node)-[:BELONGS_TO]->(g) " +
            "WHERE n.archId = $archId " +
            "WITH g, n ORDER BY coalesce(n.degree, 0) DESC LIMIT 1 " +
            "WITH g, n AS hub " +
            "OPTIONAL MATCH (hub)-[:CONNECTS_TO]-(nbr:Node) " +
            "WHERE nbr.archId = hub.archId " +
            "WITH g, hub, collect(DISTINCT nbr)[0..6] AS neighbors " +
            "WITH g, hub, neighbors, [hub] + neighbors AS allNodes " +
            "UNWIND allNodes AS node " +
            "RETURN " +
            "    g.groupId                      AS groupId, " +
            "    coalesce(g.label, '')          AS groupLabel, " +
            "    coalesce(g.description, '')    AS groupDescription, " +
            "    node.nodeId                    AS nodeId, " +
            "    coalesce(node.type, '')        AS nodeType, " +
            "    coalesce(node.label, '')       AS label, " +
            "    coalesce(node.description, '') AS description, " +
            "    coalesce(node.degree, 0)       AS degree, " +
            "    coalesce(node.path, '')        AS path, " +
            "    (node.nodeId = hub.nodeId)     AS isHub";

        List<GraphResult> results = new ArrayList<>();
        String hubNodeId = null;
        List<String> neighborIds = new ArrayList<>();
        String groupId = null;
        String groupLabel = null;
        String groupDescription = null;

        try (Session session = driver.session()) {
            var nodeResult = session.run(nodeCypher, Map.of(
                    "archId", archId,
                    "queryVector", queryVector
            ));

            while (nodeResult.hasNext()) {
                var r = nodeResult.next();

                if (groupId == null) {
                    groupId      = r.get("groupId").asString();
                    groupLabel   = r.get("groupLabel").asString("");
                    groupDescription = r.get("groupDescription").asString("");
                }

                String nodeId = r.get("nodeId").asString();
                boolean isHub = r.get("isHub").asBoolean(false);

                results.add(new GraphResult.NodeResult(
                        nodeId,
                        r.get("nodeType").asString(""),
                        r.get("label").asString(""),
                        r.get("description").asString(""),
                        r.get("degree").asDouble(0),
                        r.get("path").asString(""),
                        0.0,
                        isHub,
                        groupId
                ));

                if (isHub) {
                    hubNodeId = nodeId;
                } else {
                    neighborIds.add(nodeId);
                }
            }

            if (groupId != null) {
                results.add(new GraphResult.GroupResult(groupId, groupLabel, groupDescription));
            }

            if (hubNodeId != null && !neighborIds.isEmpty()) {
                String edgeCypher =
                    "MATCH (hub:Node {nodeId: $hubNodeId, archId: $archId})-[r:CONNECTS_TO]-(nbr:Node) " +
                    "WHERE nbr.nodeId IN $neighborIds AND nbr.archId = $archId " +
                    "RETURN r.edgeId                    AS edgeId, " +
                    "       coalesce(r.label, '')       AS label, " +
                    "       coalesce(r.description, '') AS description, " +
                    "       startNode(r).nodeId          AS fromNodeId, " +
                    "       endNode(r).nodeId            AS toNodeId";

                var edgeResult = session.run(edgeCypher, Map.of(
                        "archId", archId,
                        "hubNodeId", hubNodeId,
                        "neighborIds", neighborIds
                ));

                while (edgeResult.hasNext()) {
                    var r = edgeResult.next();
                    results.add(new GraphResult.EdgeResult(
                            r.get("edgeId").asString(),
                            r.get("fromNodeId").asString(),
                            r.get("label").asString(""),
                            r.get("toNodeId").asString(),
                            r.get("description").asString(""),
                            0.0
                    ));
                }
            }
        }

        log.info("groupSearch: archId={}, groupId={}, nodes={}, total={}",
                archId, groupId,
                results.stream().filter(r -> r instanceof GraphResult.NodeResult).count(),
                results.size());
        return results;
    }

    private void logQueryDebug(String event, String query) {
        LogSanitizer.TextSummary querySummary = LogSanitizer.summarizeText(query);
        log.debug("{} queryLength={} queryHash={}", event, querySummary.length(), querySummary.hash());
    }

    private List<Float> toFloatList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float f : arr) list.add(f);
        return list;
    }
}
