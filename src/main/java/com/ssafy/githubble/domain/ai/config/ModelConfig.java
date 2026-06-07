package com.ssafy.githubble.domain.ai.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(AiProviderProperties.class)
public class ModelConfig {

    @Bean
    public ChatModel chatLanguageModel(AiProviderProperties properties) {
        return chatModel(properties.primary(), properties.primary().temperature(), properties.primary().maxTokens());
    }

    @Bean
    public StreamingChatModel streamingChatModel(AiProviderProperties properties) {
        AiProviderProperties.Model primary = properties.primary();
        return OpenAiStreamingChatModel.builder()
                .baseUrl(primary.baseUrl())
                .apiKey(primary.apiKey())
                .modelName(primary.modelName())
                .temperature(primary.temperature())
                .maxCompletionTokens(primary.maxTokens())
                .timeout(Duration.ofSeconds(primary.timeoutSeconds()))
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    @Bean
    public ChatModel routingChatModel(AiProviderProperties properties) {
        return chatModel(properties.primary(), 0.0, 16);
    }

    @Bean
    public ChatModel judgeChatModel(AiProviderProperties properties) {
        return chatModel(properties.primary(), 0.0, 16);
    }

    @Bean
    @ConditionalOnExpression("'${ai.fallback.enabled:true}' == 'true' && '${ai.fallback.model-name:}' != ''")
    public ChatModel fallbackChatModel(AiProviderProperties properties) {
        AiProviderProperties.Fallback fallback = properties.fallback();
        return OpenAiChatModel.builder()
                .baseUrl(fallback.baseUrl())
                .apiKey(fallback.apiKey())
                .modelName(fallback.modelName())
                .temperature(fallback.temperature())
                .maxCompletionTokens(fallback.maxTokens())
                .timeout(Duration.ofSeconds(fallback.timeoutSeconds()))
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel(AiProviderProperties properties) {
        AiProviderProperties.Embedding embedding = properties.embedding();
        EmbeddingModel delegate = OpenAiEmbeddingModel.builder()
                .baseUrl(embedding.baseUrl())
                .apiKey(embedding.apiKey())
                .modelName(embedding.modelName())
                .timeout(Duration.ofSeconds(embedding.timeoutSeconds()))
                .build();
        return new LoggingEmbeddingModel(delegate, embedding.provider(), embedding.modelName());
    }

    private ChatModel chatModel(AiProviderProperties.Model model, double temperature, int maxTokens) {
        return OpenAiChatModel.builder()
                .baseUrl(model.baseUrl())
                .apiKey(model.apiKey())
                .modelName(model.modelName())
                .temperature(temperature)
                .maxCompletionTokens(maxTokens)
                .timeout(Duration.ofSeconds(model.timeoutSeconds()))
                .logRequests(false)
                .logResponses(false)
                .build();
    }
}