package com.ssafy.githubble.domain.ai.config;

import com.ssafy.githubble.domain.ai.component.GraphRagAssistant;
import com.ssafy.githubble.domain.ai.component.RagJudgeAssistant;
import com.ssafy.githubble.domain.ai.component.RoutingAssistant;
import com.ssafy.githubble.domain.ai.component.SourceAnalysisAssistant;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AssistantConfig {

    private final ChatModel chatLanguageModel;
    private final ObjectProvider<ChatModel> fallbackChatModel;
    private final AiProviderProperties properties;
    private final AiProviderFallbackSupport fallbackSupport;

    public AssistantConfig(
            @Qualifier("chatLanguageModel") ChatModel chatLanguageModel,
            @Qualifier("fallbackChatModel") ObjectProvider<ChatModel> fallbackChatModel,
            AiProviderProperties properties,
            AiProviderFallbackSupport fallbackSupport
    ) {
        this.chatLanguageModel = chatLanguageModel;
        this.fallbackChatModel = fallbackChatModel;
        this.properties = properties;
        this.fallbackSupport = fallbackSupport;
    }

    @Bean
    public GraphRagAssistant graphRagAssistant() {
        GraphRagAssistant primary = AiServices.builder(GraphRagAssistant.class)
                .chatModel(chatLanguageModel)
                .build();
        ChatModel fallbackModel = fallbackChatModel.getIfAvailable();
        if (fallbackModel == null) {
            return primary;
        }
        GraphRagAssistant fallback = AiServices.builder(GraphRagAssistant.class)
                .chatModel(fallbackModel)
                .build();
        return (context, question) -> fallbackSupport.call("answer", provider(), primaryModel(), fallbackModel(),
                () -> primary.ask(context, question),
                () -> fallback.ask(context, question));
    }

    @Bean
    public SourceAnalysisAssistant sourceAnalysisAssistant() {
        SourceAnalysisAssistant primary = AiServices.builder(SourceAnalysisAssistant.class)
                .chatModel(chatLanguageModel)
                .build();
        ChatModel fallbackModel = fallbackChatModel.getIfAvailable();
        if (fallbackModel == null) {
            return primary;
        }
        SourceAnalysisAssistant fallback = AiServices.builder(SourceAnalysisAssistant.class)
                .chatModel(fallbackModel)
                .build();
        return (sourcePath, sourceContent, question) -> fallbackSupport.call("source_analysis", provider(), primaryModel(), fallbackModel(),
                () -> primary.analyze(sourcePath, sourceContent, question),
                () -> fallback.analyze(sourcePath, sourceContent, question));
    }

    @Bean
    public RagJudgeAssistant ragJudgeAssistant(@Qualifier("judgeChatModel") ChatModel judgeChatModel) {
        RagJudgeAssistant primary = AiServices.builder(RagJudgeAssistant.class)
                .chatModel(judgeChatModel)
                .build();
        ChatModel fallbackModel = fallbackChatModel.getIfAvailable();
        if (fallbackModel == null) {
            return primary;
        }
        RagJudgeAssistant fallback = AiServices.builder(RagJudgeAssistant.class)
                .chatModel(fallbackModel)
                .build();
        return new RagJudgeAssistant() {
            @Override
            public String judge(String question, String answer) {
                return fallbackSupport.call("judge", provider(), primaryModel(), fallbackModel(),
                        () -> primary.judge(question, answer),
                        () -> fallback.judge(question, answer));
            }

            @Override
            public String judgeContext(String question, String context) {
                return fallbackSupport.call("judge_context", provider(), primaryModel(), fallbackModel(),
                        () -> primary.judgeContext(question, context),
                        () -> fallback.judgeContext(question, context));
            }
        };
    }

    @Bean
    public RoutingAssistant routingAssistant(@Qualifier("routingChatModel") ChatModel routingChatModel) {
        RoutingAssistant primary = AiServices.builder(RoutingAssistant.class)
                .chatModel(routingChatModel)
                .build();
        ChatModel fallbackModel = fallbackChatModel.getIfAvailable();
        if (fallbackModel == null) {
            return primary;
        }
        RoutingAssistant fallback = AiServices.builder(RoutingAssistant.class)
                .chatModel(fallbackModel)
                .build();
        return query -> fallbackSupport.call("routing", provider(), primaryModel(), fallbackModel(),
                () -> primary.classify(query),
                () -> fallback.classify(query));
    }

    private String provider() {
        return properties.primary().provider();
    }

    private String primaryModel() {
        return properties.primary().modelName();
    }

    private String fallbackModel() {
        return properties.fallback().modelName();
    }
}
