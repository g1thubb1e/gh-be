package com.ssafy.githubble.domain.ai.component;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface RagJudgeAssistant {

    @SystemMessage(fromResource = "prompts/answer-judge-system.txt")
    @UserMessage(fromResource = "prompts/answer-judge-user.txt")
    String judge(@V("question") String question, @V("answer") String answer);

    @SystemMessage(fromResource = "prompts/rag-judge-system.txt")
    @UserMessage(fromResource = "prompts/rag-judge-user.txt")
    String judgeContext(@V("question") String question, @V("answer") String context);
}