package com.ssafy.githubble.domain.github.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GitDiagramClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("GitDiagram 요청 JSON은 GD DTO가 받는 snake_case 필드를 사용한다")
    void gitDiagramGenerateRequestUsesGdFieldNames() throws JsonProcessingException {
        GitDiagramClient.GitDiagramGenerateRequest request = new GitDiagramClient.GitDiagramGenerateRequest(
                "owner",
                "repo",
                "github_pat_secret",
                "abc123",
                "owner/repo",
                "run-123"
        );

        String json = objectMapper.writeValueAsString(request);

        assertThat(json).contains("\"github_pat\":\"github_pat_secret\"");
        assertThat(json).contains("\"commit_sha\":\"abc123\"");
        assertThat(json).contains("\"owner_repo\":\"owner/repo\"");
        assertThat(json).contains("\"generation_run_id\":\"run-123\"");
        assertThat(json).doesNotContain("githubPat", "commitSha", "ownerRepo", "generationRunId");
    }

    @Test
    @DisplayName("GitDiagram HTTP 실패 응답 요약은 body 원문 없이 메타데이터만 남긴다")
    void summarizeHttpFailureResponseKeepsOnlyMetadata() {
        String rawBody = """
                {"detail":[{"loc":["body","github_pat"],"msg":"bad token github_pat_secret"}],
                 "githubPat":"camel_secret",
                 "github_pat":"snake_secret",
                 "Authorization":"Bearer jwt_secret",
                 "Cookie":"refresh=refresh_secret",
                 "api_key":"openai_secret",
                 "nested":{"access_token":"access_secret","token":"token_secret","jwt":"jwt_secret"}}
                """;

        GitDiagramClient.HttpFailureResponseSummary summary = GitDiagramClient.summarizeHttpFailureResponse(rawBody);

        assertThat(summary.bodyLength()).isEqualTo(rawBody.length());
        assertThat(summary.errorCode()).isEqualTo("HTTP_RESPONSE_BODY_PRESENT");
    }

    @Test
    @DisplayName("SSE 오류 문자열은 원문 대신 길이만 기록할 수 있게 계산한다")
    void safeLengthKeepsOnlyLength() {
        assertThat(GitDiagramClient.safeLength("token_secret")).isEqualTo(12);
        assertThat(GitDiagramClient.safeLength(null)).isZero();
    }
}
