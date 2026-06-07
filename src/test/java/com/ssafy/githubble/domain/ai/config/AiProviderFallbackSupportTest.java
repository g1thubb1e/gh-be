package com.ssafy.githubble.domain.ai.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiProviderFallbackSupportTest {

    @Test
    @DisplayName("primary 호출이 성공하면 fallback을 호출하지 않는다")
    void primarySuccessDoesNotCallFallback() {
        AtomicInteger fallbackCalls = new AtomicInteger();
        AiProviderFallbackSupport support = new AiProviderFallbackSupport();

        String result = support.call("routing", "openrouter", "openrouter/free", "fallback-model",
                () -> "primary-result",
                () -> {
                    fallbackCalls.incrementAndGet();
                    return "fallback-result";
                });

        assertThat(result).isEqualTo("primary-result");
        assertThat(fallbackCalls).hasValue(0);
    }

    @Test
    @DisplayName("primary 호출이 실패하고 fallback 모델이 있으면 fallback 결과를 반환한다")
    void primaryFailureCallsFallbackWhenFallbackModelExists() {
        AiProviderFallbackSupport support = new AiProviderFallbackSupport();

        String result = support.call("answer", "openrouter", "openrouter/free", "qwen/qwen3",
                () -> { throw new IllegalStateException("primary unavailable"); },
                () -> "fallback-result");

        assertThat(result).isEqualTo("fallback-result");
    }

    @Test
    @DisplayName("fallback 모델이 비어 있으면 primary 예외를 그대로 던진다")
    void blankFallbackModelRethrowsPrimaryFailure() {
        AiProviderFallbackSupport support = new AiProviderFallbackSupport();

        assertThatThrownBy(() -> support.call("answer", "openrouter", "openrouter/free", "",
                () -> { throw new IllegalStateException("primary unavailable"); },
                () -> "fallback-result"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("primary unavailable");
    }
}
