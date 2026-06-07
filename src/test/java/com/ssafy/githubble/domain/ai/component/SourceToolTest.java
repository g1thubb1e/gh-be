package com.ssafy.githubble.domain.ai.component;

import com.ssafy.githubble.domain.github.dto.response.GitHubFileContentResponse;
import com.ssafy.githubble.domain.github.service.GitHubRepositoryParseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SourceToolTest {

    @Test
    @DisplayName("SourceTool은 하드코딩된 사용자 대신 생성자로 받은 appuserId로 파일을 조회한다")
    void analyzeSourceUsesProvidedAppuserId() {
        GitHubRepositoryParseService parseService = mock(GitHubRepositoryParseService.class);
        SourceAnalysisAssistant assistant = mock(SourceAnalysisAssistant.class);
        Long appuserId = 42L;
        SourceTool sourceTool = new SourceTool(appuserId, "owner", "repo", parseService, assistant);
        GitHubFileContentResponse file = new GitHubFileContentResponse(
                "owner", "repo", "main", "file", "src/App.java", "App.java", "sha", 12L,
                "base64", "class App {}", true, false, null, "https://example.com", false);
        when(parseService.getFileContent(eq(appuserId), eq("owner"), eq("repo"), eq("src/App.java"), eq(null)))
                .thenReturn(file);
        when(assistant.analyze(eq("src/App.java"), eq("class App {}"), eq("질문")))
                .thenReturn("분석 결과");

        sourceTool.analyzeSource("src/App.java", "질문");

        verify(parseService).getFileContent(eq(appuserId), eq("owner"), eq("repo"), eq("src/App.java"), eq(null));
    }
}
