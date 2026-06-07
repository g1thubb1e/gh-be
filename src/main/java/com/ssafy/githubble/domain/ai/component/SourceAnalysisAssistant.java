package com.ssafy.githubble.domain.ai.component;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface SourceAnalysisAssistant {

    @SystemMessage(fromResource = "prompts/source-analysis-system.txt")
    @UserMessage(fromResource = "prompts/source-analysis-user.txt")
    String analyze(@V("sourcePath") String sourcePath,
                   @V("sourceContent") String sourceContent,
                   @V("question") String question);
}
