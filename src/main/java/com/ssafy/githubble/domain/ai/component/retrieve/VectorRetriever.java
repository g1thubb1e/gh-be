package com.ssafy.githubble.domain.ai.component.retrieve;

import com.ssafy.githubble.global.util.LogSanitizer;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class VectorRetriever {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    private final int topKNode = 8;
    private final int topKEdge = 12;
    private final double minScore = 0.085;

    public List<GraphResult> findSimilarNodes(String archId, String query) {
        return findSimilar(archId, query, "node");
    }

    public List<GraphResult> findSimilarEdges(String archId, String query) {
        return findSimilar(archId, query, "edge");
    }



    private List<GraphResult> findSimilar(String archId, String query, String kind) {
        var queryEmbedding = embeddingModel.embed(query).content();
        var filter = MetadataFilterBuilder.metadataKey("archId").isEqualTo(archId)
                .and(MetadataFilterBuilder.metadataKey("kind").isEqualTo(kind));

        var request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults("node".equals(kind) ? topKNode : topKEdge)
                .minScore(minScore)
                .filter(filter)
                .build();

        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches()
                .stream()
                .filter(m -> m.score() >= minScore)
                .toList();
        log.info("Vector search: kind={}, archId={}, hits={}", kind, archId, matches.size());
        LogSanitizer.TextSummary querySummary = LogSanitizer.summarizeText(query);
        log.debug("Vector search queryLength={} queryHash={}", querySummary.length(), querySummary.hash());

        return matches.stream()
                .map(m -> toGraphResult(kind, m))
                .toList();
    }

    private GraphResult toGraphResult(String kind, EmbeddingMatch<TextSegment> match) {
        Metadata md = match.embedded().metadata();
        if ("node".equals(kind)) {
            return GraphResult.NodeResult.of(
                    md.getString("nodeId"),
                    md.getString("nodeType"),
                    md.getString("label"),
                    md.getString("description"),
                    0.0,
                    md.getString("path"),
                    0.0
            );
        } else {
            return new GraphResult.EdgeResult(
                    md.getString("refId"),
                    md.getString("fromNodeId"),
                    md.getString("label"),
                    md.getString("toNodeId"),
                    md.getString("description"),
                    0.0
            );
        }
    }
}
