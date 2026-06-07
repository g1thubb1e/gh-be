package com.ssafy.githubble.domain.ai.config;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingEmbeddingModelTest {

    @Test
    @DisplayName("embedAll 호출을 delegate에 위임하고 결과를 반환한다")
    void embedAllDelegatesAndReturnsResult() {
        AtomicInteger calls = new AtomicInteger();
        EmbeddingModel delegate = segments -> {
            calls.incrementAndGet();
            return Response.from(List.of(Embedding.from(new float[]{1.0f, 2.0f, 3.0f})));
        };
        LoggingEmbeddingModel model = new LoggingEmbeddingModel(delegate, "openrouter", "openai/text-embedding-3-large");

        Response<List<Embedding>> response = model.embedAll(List.of(TextSegment.from("hello")));

        assertThat(calls).hasValue(1);
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).dimension()).isEqualTo(3);
    }
}
