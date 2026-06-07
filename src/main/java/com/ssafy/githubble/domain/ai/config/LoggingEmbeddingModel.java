package com.ssafy.githubble.domain.ai.config;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class LoggingEmbeddingModel implements EmbeddingModel {

    private final EmbeddingModel delegate;
    private final String provider;
    private final String modelName;

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
        long startedAt = System.nanoTime();
        int inputCount = segments == null ? 0 : segments.size();
        log.info("ai.embedding.event event=started provider={} model={} operation=embedding inputCount={}",
                provider, modelName, inputCount);
        try {
            Response<List<Embedding>> response = delegate.embedAll(segments);
            int embeddingDim = response.content() == null || response.content().isEmpty()
                    ? 0
                    : response.content().get(0).dimension();
            log.info("ai.embedding.event event=succeeded provider={} model={} operation=embedding inputCount={} embeddingDim={} durationMs={}",
                    provider, modelName, inputCount, embeddingDim, elapsedMs(startedAt));
            return response;
        } catch (RuntimeException e) {
            log.warn("ai.embedding.event event=failed provider={} model={} operation=embedding inputCount={} status={} errorCode={} exceptionType={} durationMs={}",
                    provider, modelName, inputCount, status(e), errorCode(e), e.getClass().getName(), elapsedMs(startedAt));
            throw e;
        }
    }

    @Override
    public int dimension() {
        return delegate.dimension();
    }

    @Override
    public String modelName() {
        return modelName;
    }

    private String status(Throwable failure) {
        if (failure instanceof HttpException httpException) {
            return String.valueOf(httpException.statusCode());
        }
        if (failure instanceof RateLimitException) {
            return "429";
        }
        if (failure instanceof TimeoutException) {
            return "503";
        }
        return "-";
    }

    private String errorCode(Throwable failure) {
        if (failure instanceof RateLimitException) {
            return "ai_provider_rate_limited";
        }
        if (failure instanceof TimeoutException) {
            return "ai_provider_timeout";
        }
        if (failure instanceof HttpException httpException) {
            int statusCode = httpException.statusCode();
            if (statusCode == 401 || statusCode == 403) {
                return "ai_provider_unauthorized";
            }
            if (statusCode == 429) {
                return "ai_provider_rate_limited";
            }
            if (statusCode >= 500) {
                return "ai_provider_unavailable";
            }
            return "ai_provider_http_error";
        }
        return "ai_provider_error";
    }

    private long elapsedMs(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }
}
