package com.ssafy.githubble.domain.ai.component;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface GraphRagAssistant {

    @SystemMessage(fromResource = "prompts/graphrag-system.txt")
    @UserMessage(fromResource = "prompts/graphrag-user.txt")
    String ask(@V("context") String context, @V("question") String question);
}
