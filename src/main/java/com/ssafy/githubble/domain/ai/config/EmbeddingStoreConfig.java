package com.ssafy.githubble.domain.ai.config;

import com.ssafy.githubble.domain.ai.component.adaptor.JpaEmbeddingStoreAdapter;
import com.ssafy.githubble.domain.ai.repository.EmbeddingRepository;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingStoreConfig {

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(EmbeddingRepository embeddingRepository) {
        return new JpaEmbeddingStoreAdapter(embeddingRepository);
    }
}
