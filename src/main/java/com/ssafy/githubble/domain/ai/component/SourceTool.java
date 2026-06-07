package com.ssafy.githubble.domain.ai.component;

import com.ssafy.githubble.domain.github.dto.response.GitHubFileContentResponse;
import com.ssafy.githubble.domain.github.service.GitHubRepositoryParseService;
import com.ssafy.githubble.global.util.LogSanitizer;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SourceTool {

    private final Long appuserId;
    private final String owner;
    private final String repo;
    private final GitHubRepositoryParseService gitHubRepositoryParseService;
    private final SourceAnalysisAssistant sourceAnalysisAssistant;

    public SourceTool(Long appuserId, String owner, String repo,
                      GitHubRepositoryParseService gitHubRepositoryParseService,
                      SourceAnalysisAssistant sourceAnalysisAssistant) {
        this.appuserId = appuserId;
        this.owner = owner;
        this.repo = repo;
        this.gitHubRepositoryParseService = gitHubRepositoryParseService;
        this.sourceAnalysisAssistant = sourceAnalysisAssistant;
    }

    @Tool("저장소의 소스 파일을 읽어 질문에 답변한다. path는 저장소 내 파일 경로(예: src/main/App.java)이며, RAG 컨텍스트의 노드 path 필드에서 가져온다.")
    public String analyzeSource(String path, String question) {
        LogSanitizer.TextSummary questionSummary = LogSanitizer.summarizeText(question);
        log.info("ai.source_tool.event event=analyze_source_started owner={} repo={} path={} questionLength={} questionHash={}",
                owner, repo, path, questionSummary.length(), questionSummary.hash());
        GitHubFileContentResponse file =
                gitHubRepositoryParseService.getFileContent(appuserId, owner, repo, path, null);
        if (!file.contentAvailable()) {
            log.info("[SourceTool] content unavailable — path='{}', reason={}", path, file.unavailableReason());
            return "";
        }
        return sourceAnalysisAssistant.analyze(path, file.content(), question);
    }
}
