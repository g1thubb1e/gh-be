package com.ssafy.githubble.domain.ai.component.retrieve;

import com.ssafy.githubble.domain.ai.component.QueryIntent;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class HybridRetriever {

    private final VectorRetriever vectorRetriever;
    private final GraphRetriever graphRetriever;

    @Value("${graphrag.retriever.hybridTopK:5}")
    private int topK;

    @Value("${graphrag.retriever.fulltextWeight:0.6}")
    private double fulltextWeight;

    @Value("${graphrag.retriever.vectorWeight:0.4}")
    private double vectorWeight;

//    public List<VectorRetriever.ScoredResult> graphAndVectorRetrieve(String archId, String query) {
//        List<VectorRetriever.ScoredResult> graphResults = graphRetriever.graphSearch(archId, query, 5, 4, 0.65);
//        List<VectorRetriever.ScoredResult> vectorNodeResults = vectorRetriever.findSimilarNodes(archId, query);
//        List<VectorRetriever.ScoredResult> vectorEdgeResults = vectorRetriever.findSimilarEdges(archId, query);
//
//        Map<String, VectorRetriever.ScoredResult> merged = new LinkedHashMap<>();
//
//        for (VectorRetriever.ScoredResult r : graphResults) {
//            String key = r.type() + ":" + r.id();
//            merged.merge(key, new VectorRetriever.ScoredResult(
//                    r.type(), r.id(), r.label(), r.snippet(), r.score() * fulltextWeight
//            ), (a, b) -> new VectorRetriever.ScoredResult(
//                    a.type(), a.id(), a.label(), a.snippet(), a.score() + b.score()
//            ));
//        }
//
//        for (VectorRetriever.ScoredResult r : vectorNodeResults) {
//            String key = r.type() + ":" + r.id();
//            merged.merge(key, new VectorRetriever.ScoredResult(
//                    r.type(), r.id(), r.label(), r.snippet(), r.score() * vectorWeight
//            ), (a, b) -> new VectorRetriever.ScoredResult(
//                    a.type(), a.id(), a.label(), a.snippet(), a.score() + b.score()
//            ));
//        }
//
//        for (VectorRetriever.ScoredResult r : vectorEdgeResults) {
//            String key = r.type() + ":" + r.id();
//            merged.merge(key, new VectorRetriever.ScoredResult(
//                    r.type(), r.id(), r.label(), r.snippet(), r.score() * vectorWeight
//            ), (a, b) -> new VectorRetriever.ScoredResult(
//                    a.type(), a.id(), a.label(), a.snippet(), a.score() + b.score()
//            ));
//        }
//
//        List<VectorRetriever.ScoredResult> ranked = new ArrayList<>(merged.values());
//        ranked.sort(Comparator.comparingDouble(VectorRetriever.ScoredResult::score).reversed());
//
//        List<VectorRetriever.ScoredResult> topResults = ranked.stream().limit(topK).toList();
//        log.info("HybridRetriever: archId={}, merged={}, returned={}", archId, merged.size(), topResults.size());
//        return topResults;
//    }


}
