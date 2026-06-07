package com.ssafy.githubble.domain.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai")
public record AiProviderProperties(
        Model primary,
        Fallback fallback,
        Embedding embedding
) {
    public AiProviderProperties {
        primary = primary == null ? Model.primaryDefaults() : primary.withDefaults(Model.primaryDefaults());
        fallback = fallback == null ? Fallback.defaults() : fallback.withDefaults(Fallback.defaults());
        embedding = embedding == null ? Embedding.defaults() : embedding.withDefaults(Embedding.defaults());
    }

    public record Model(
            String provider,
            String baseUrl,
            String apiKey,
            String modelName,
            double temperature,
            int maxTokens,
            int timeoutSeconds
    ) {
        static Model primaryDefaults() {
            return new Model("openrouter", "https://openrouter.ai/api/v1", "", "deepseek/deepseek-v4-flash", 0.2, 4096, 300);
        }

        Model withDefaults(Model defaults) {
            return new Model(
                    defaultString(provider, defaults.provider),
                    defaultString(baseUrl, defaults.baseUrl),
                    defaultString(apiKey, defaults.apiKey),
                    defaultString(modelName, defaults.modelName),
                    temperature == 0.0 ? defaults.temperature : temperature,
                    maxTokens == 0 ? defaults.maxTokens : maxTokens,
                    timeoutSeconds == 0 ? defaults.timeoutSeconds : timeoutSeconds
            );
        }
    }

    public record Fallback(
            boolean enabled,
            String provider,
            String baseUrl,
            String apiKey,
            String modelName,
            double temperature,
            int maxTokens,
            int timeoutSeconds
    ) {
        static Fallback defaults() {
            return new Fallback(true, "openrouter", "https://openrouter.ai/api/v1", "", "", 0.2, 2048, 120);
        }

        Fallback withDefaults(Fallback defaults) {
            return new Fallback(
                    enabled,
                    defaultString(provider, defaults.provider),
                    defaultString(baseUrl, defaults.baseUrl),
                    defaultString(apiKey, defaults.apiKey),
                    defaultString(modelName, defaults.modelName),
                    temperature == 0.0 ? defaults.temperature : temperature,
                    maxTokens == 0 ? defaults.maxTokens : maxTokens,
                    timeoutSeconds == 0 ? defaults.timeoutSeconds : timeoutSeconds
            );
        }
    }

    public record Embedding(
            String provider,
            String baseUrl,
            String apiKey,
            String modelName,
            int dimension,
            int timeoutSeconds
    ) {
        static Embedding defaults() {
            return new Embedding("openrouter", "https://openrouter.ai/api/v1", "", "openai/text-embedding-3-large", 3072, 60);
        }

        Embedding withDefaults(Embedding defaults) {
            return new Embedding(
                    defaultString(provider, defaults.provider),
                    defaultString(baseUrl, defaults.baseUrl),
                    defaultString(apiKey, defaults.apiKey),
                    defaultString(modelName, defaults.modelName),
                    dimension == 0 ? defaults.dimension : dimension,
                    timeoutSeconds == 0 ? defaults.timeoutSeconds : timeoutSeconds
            );
        }
    }

    private static String defaultString(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
