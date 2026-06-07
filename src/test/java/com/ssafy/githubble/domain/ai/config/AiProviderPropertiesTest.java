package com.ssafy.githubble.domain.ai.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class AiProviderPropertiesTest {

    @Test
    @DisplayName("AI provider 기본값은 OpenRouter primary와 OpenAI embedding을 사용한다")
    void defaultPropertiesUseOpenRouterPrimaryAndOpenAiEmbedding() {
        AiProviderProperties properties = Binder.get(new MockEnvironment())
                .bindOrCreate("ai", AiProviderProperties.class);

        assertThat(properties.primary().provider()).isEqualTo("openrouter");
        assertThat(properties.primary().baseUrl()).isEqualTo("https://openrouter.ai/api/v1");
        assertThat(properties.primary().modelName()).isEqualTo("deepseek/deepseek-v4-flash");
        assertThat(properties.fallback().enabled()).isTrue();
        assertThat(properties.fallback().provider()).isEqualTo("openrouter");
        assertThat(properties.fallback().baseUrl()).isEqualTo("https://openrouter.ai/api/v1");
        assertThat(properties.embedding().provider()).isEqualTo("openrouter");
        assertThat(properties.embedding().baseUrl()).isEqualTo("https://openrouter.ai/api/v1");
        assertThat(properties.embedding().modelName()).isEqualTo("openai/text-embedding-3-large");
    }

    @Test
    @DisplayName("환경변수로 primary, fallback, embedding 모델을 분리 설정할 수 있다")
    void propertiesCanBeOverriddenIndependently() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("ai.primary.model-name", "deepseek/deepseek-v4-flash")
                .withProperty("ai.fallback.model-name", "google/gemini-2.0-flash-001")
                .withProperty("ai.embedding.model-name", "openai/text-embedding-3-large")
                .withProperty("ai.embedding.dimension", "3072");

        AiProviderProperties properties = Binder.get(environment)
                .bindOrCreate("ai", AiProviderProperties.class);

        assertThat(properties.primary().modelName()).isEqualTo("deepseek/deepseek-v4-flash");
        assertThat(properties.fallback().modelName()).isEqualTo("google/gemini-2.0-flash-001");
        assertThat(properties.embedding().modelName()).isEqualTo("openai/text-embedding-3-large");
        assertThat(properties.embedding().dimension()).isEqualTo(3072);
    }
}
