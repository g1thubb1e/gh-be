package com.ssafy.githubble.domain.ai.component.adaptor;

import com.ssafy.githubble.domain.ai.entity.EmbeddingEntity;
import com.ssafy.githubble.domain.ai.repository.EmbeddingRepository;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.logical.And;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@RequiredArgsConstructor
public class JpaEmbeddingStoreAdapter implements EmbeddingStore<TextSegment> {

    private final EmbeddingRepository repo;

    @Override
    public String add(Embedding embedding) {
        throw new UnsupportedOperationException("add(Embedding) is not supported");
    }

    @Override
    public void add(String id, Embedding embedding) {
        throw new UnsupportedOperationException("add(String, Embedding) is not supported");
    }

    @Override
    public String add(Embedding embedding, TextSegment embedded) {
        throw new UnsupportedOperationException("add(Embedding, TextSegment) is not supported");
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        throw new UnsupportedOperationException("addAll without segments is not supported");
    }

    @Override
    @Transactional
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> segments) {
        if (embeddings.size() != segments.size()) {
            throw new IllegalArgumentException("embeddings/segments size mismatch");
        }
        LocalDateTime now = LocalDateTime.now();
        List<EmbeddingEntity> entities = new ArrayList<>(segments.size());
        for (int i = 0; i < segments.size(); i++) {
            TextSegment seg = segments.get(i);
            Metadata md = seg.metadata();
            EmbeddingEntity e = new EmbeddingEntity();
            e.setId(UUID.randomUUID());
            e.setArchId(md.getString("archId"));
            e.setKind(md.getString("kind"));
            e.setRefId(md.getString("refId"));
            e.setLabel(md.getString("label"));
            e.setContent(seg.text());
            e.setNodeId(md.getString("nodeId"));
            e.setNodeType(md.getString("nodeType"));
            e.setDescription(md.getString("description"));
            e.setPath(md.getString("path"));
            e.setFromNodeId(md.getString("fromNodeId"));
            e.setToNodeId(md.getString("toNodeId"));
            e.setEmbedding(embeddings.get(i).vector());
            e.setCreatedAt(now);
            entities.add(e);
        }
        repo.saveAll(entities);
        return entities.stream().map(x -> x.getId().toString()).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest req) {
        FilterKeys keys = parseEqualFilters(req.filter());
        if (keys.archId() == null || keys.kind() == null) {
            throw new UnsupportedOperationException(
                    "search requires And(archId, kind) IsEqualTo filters");
        }
        int topK = Math.max(1, Math.min(req.maxResults(), 1000));
        String queryVec = toVectorString(req.queryEmbedding().vector());
        List<EmbeddingRepository.EmbeddingSearchRow> rows = repo.searchByCosine(keys.archId(), keys.kind(), queryVec, topK);
        List<EmbeddingMatch<TextSegment>> matches = rows.stream()
                .map(r -> {
                    Map<String, Object> meta = new HashMap<>();
                    meta.put("archId",       r.getArchId());
                    meta.put("kind",         r.getKind());
                    meta.put("refId",        r.getRefId());
                    meta.put("label",        r.getLabel() == null ? "" : r.getLabel());
                    meta.put("nodeId",       r.getNodeId() == null ? "" : r.getNodeId());
                    meta.put("nodeType",     r.getNodeType() == null ? "" : r.getNodeType());
                    meta.put("description",  r.getDescription() == null ? "" : r.getDescription());
                    meta.put("path",         r.getPath() == null ? "" : r.getPath());
                    meta.put("fromNodeId",   r.getFromNodeId() == null ? "" : r.getFromNodeId());
                    meta.put("toNodeId",     r.getToNodeId() == null ? "" : r.getToNodeId());
                    return new EmbeddingMatch<>(
                            r.getScore(),
                            r.getId().toString(),
                            null,
                            TextSegment.from(r.getContent(), Metadata.from(meta))
                    );
                }).toList();
        return new EmbeddingSearchResult<>(matches);
    }

    @Override
    @Transactional
    public void removeAll(Filter filter) {
        FilterKeys keys = parseEqualFilters(filter);
        if (keys.archId() == null) {
            throw new UnsupportedOperationException(
                    "removeAll requires IsEqualTo(\"archId\", ...)");
        }
        repo.deleteByArchId(keys.archId());
    }

    /** Filter 파서: IsEqualTo("archId")·IsEqualTo("kind") 및 그 And 조합만 수용. */
    static FilterKeys parseEqualFilters(Filter filter) {
        String archId = null, kind = null;
        Deque<Filter> stack = new ArrayDeque<>();
        if (filter != null) stack.push(filter);
        while (!stack.isEmpty()) {
            Filter f = stack.pop();
            if (f instanceof And and) {
                stack.push(and.left());
                stack.push(and.right());
            } else if (f instanceof IsEqualTo eq) {
                switch (eq.key()) {
                    case "archId" -> archId = String.valueOf(eq.comparisonValue());
                    case "kind" -> kind = String.valueOf(eq.comparisonValue());
                    default -> throw new UnsupportedOperationException(
                            "unsupported filter key: " + eq.key());
                }
            } else {
                throw new UnsupportedOperationException(
                        "unsupported filter type: " + f.getClass().getSimpleName());
            }
        }
        return new FilterKeys(archId, kind);
    }

    /** float[] → PostgreSQL vector literal string, e.g. "[0.1,0.2,...]". */
    static String toVectorString(float[] vec) {
        StringBuilder sb = new StringBuilder(vec.length * 10).append('[');
        for (int i = 0; i < vec.length; i++) {
            if (!Float.isFinite(vec[i])) {
                throw new IllegalArgumentException(
                        "embedding contains non-finite value at index " + i + ": " + vec[i]);
            }
            if (i > 0) sb.append(',');
            sb.append(vec[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    record FilterKeys(String archId, String kind) {}
}
