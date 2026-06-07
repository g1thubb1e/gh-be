package com.ssafy.githubble.domain.ai.config;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Slf4j
@Component
public class AiProviderFallbackSupport {

    public <T> T call(
            String operation,
            String provider,
            String primaryModel,
            String fallbackModel,
            Supplier<T> primaryCall,
            Supplier<T> fallbackCall
    ) {
        long primaryStartedAt = System.nanoTime();
        log.info("ai.provider.event event=started stage=primary provider={} model={} operation={}",
                provider, primaryModel, operation);
        try {
            T result = primaryCall.get();
            log.info("ai.provider.event event=succeeded stage=primary provider={} model={} operation={} durationMs={}",
                    provider, primaryModel, operation, elapsedMs(primaryStartedAt));
            return result;
        } catch (RuntimeException primaryFailure) {
            log.warn("ai.provider.event event=failed stage=primary provider={} model={} operation={} status={} errorCode={} exceptionType={} durationMs={}",
                    provider, primaryModel, operation, status(primaryFailure), errorCode(primaryFailure),
                    primaryFailure.getClass().getName(), elapsedMs(primaryStartedAt));
            if (fallbackModel == null || fallbackModel.isBlank()) {
                log.warn("ai.fallback.event event=skipped primaryProvider={} primaryModel={} operation={} reason=no_fallback_model",
                        provider, primaryModel, operation);
                throw primaryFailure;
            }
            return callFallback(operation, provider, primaryModel, fallbackModel, fallbackCall, primaryFailure);
        }
    }

    private <T> T callFallback(
            String operation,
            String provider,
            String primaryModel,
            String fallbackModel,
            Supplier<T> fallbackCall,
            RuntimeException primaryFailure
    ) {
        long fallbackStartedAt = System.nanoTime();
        log.warn("ai.fallback.event event=started primaryProvider={} primaryModel={} fallbackProvider={} fallbackModel={} operation={} reason=primary_failed",
                provider, primaryModel, provider, fallbackModel, operation);
        try {
            T result = fallbackCall.get();
            log.info("ai.fallback.event event=succeeded fallbackProvider={} fallbackModel={} operation={} durationMs={}",
                    provider, fallbackModel, operation, elapsedMs(fallbackStartedAt));
            return result;
        } catch (RuntimeException fallbackFailure) {
            primaryFailure.addSuppressed(fallbackFailure);
            log.error("ai.fallback.event event=failed fallbackProvider={} fallbackModel={} operation={} status={} errorCode={} exceptionType={} durationMs={}",
                    provider, fallbackModel, operation, status(fallbackFailure), errorCode(fallbackFailure),
                    fallbackFailure.getClass().getName(), elapsedMs(fallbackStartedAt));
            throw primaryFailure;
        }
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
