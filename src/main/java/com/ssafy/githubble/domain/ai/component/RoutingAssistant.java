package com.ssafy.githubble.domain.ai.component;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface RoutingAssistant {

    @SystemMessage(fromResource = "prompts/routing-system.txt")
    @UserMessage(fromResource = "prompts/routing-user.txt")
    String classify(@V("query") String query);
}
