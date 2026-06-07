package com.ssafy.githubble.domain.github.controller;

import com.ssafy.githubble.domain.github.dto.response.*;
import com.ssafy.githubble.domain.github.service.GitHubRepositoryParseService;
import com.ssafy.githubble.domain.github.service.GitHubRepositoryService;
import com.ssafy.githubble.domain.github.service.GitHubRepositoryGenerationService;
import com.ssafy.githubble.global.code.ErrorCode;
import com.ssafy.githubble.global.code.SuccessCode;
import com.ssafy.githubble.global.dto.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/v1/github")
@Tag(name = "GitHub Repository", description = "로그인 사용자의 GitHub 권한으로 저장소 메타데이터와 구조를 파싱하는 API")
@SecurityRequirement(name = "cookieAuth")
public class GitHubRepositoryController {

    private final GitHubRepositoryService gitHubRepositoryService;
    private final GitHubRepositoryParseService gitHubRepositoryParseService;
    private final GitHubRepositoryGenerationService gitHubRepositoryGenerationService;
    private final com.ssafy.githubble.global.etc.JobManager jobManager;

    @Operation(
            summary = "레포지토리 생성 작업 SSE 구독",
            description = "owner/repo 기준으로 진행 중인 레포지토리 생성 작업의 단계별 이벤트를 SSE로 구독합니다."
    )
    @GetMapping("/{owner}/{repo}/sse")
    public SseEmitter subscribe(
            @PathVariable String owner,
            @PathVariable String repo){
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);
        String jobId = owner + "/" + repo;
        return gitHubRepositoryGenerationService.subscribeSSE(jobId, emitter);
    }

    @Operation(
            summary = "레포지토리 관련 데이터를 생성합니다.",
            description = "sse 이벤트를 발행합니다."
    )
    @PostMapping("/{owner}/{repo}/generation")
    public ResponseEntity<ApiResult<Void>> startEvent(
            HttpServletRequest httpServletRequest,
            @PathVariable String owner,
            @PathVariable String repo
    ) {
        Long appuserId = (Long) httpServletRequest.getAttribute("appuserId");
        gitHubRepositoryGenerationService.generate(appuserId, owner, repo);
        return ApiResult.success(SuccessCode.SUCCESS);
    }

    @Operation(
            summary = "레포지토리 다이어그램을 조회합니다.",
            description = "다이어그램과 메타데이터를 반환합니다."
    )
    @GetMapping("/{owner}/{repo}/diagram")
    public ResponseEntity<ApiResult<GitDiagramResponse>> getDiagram(
            @PathVariable String owner,
            @PathVariable String repo
    ) {
        GitDiagramResponse response = gitHubRepositoryService.getDiagram(owner, repo);
        return ApiResult.success(SuccessCode.SUCCESS, response);
    }

    @Operation(
            summary = "레포지토리 AI 요약 정보를 조회합니다.",
            description = "레포 요약을 반환합니다."
    )
    @GetMapping("/{owner}/{repo}/summary")
    public ResponseEntity<ApiResult<GitHubRepoSummaryResponse>> getSummary(
            @PathVariable String owner,
            @PathVariable String repo
    ) {
        GitHubRepoSummaryResponse response = gitHubRepositoryService.getSummary(owner, repo);
        return ApiResult.success(SuccessCode.SUCCESS, response);
    }

    @Operation(
            summary = "레포지토리 테크 스택 정보를 조회합니다.",
            description = "레포 테크 스택을 반환합니다."
    )
    @GetMapping("/{owner}/{repo}/tech-stack")
    public ResponseEntity<ApiResult<List<GithubRepoTechStackResponse>>> getRepositoryTechStack(
            @PathVariable String owner,
            @PathVariable String repo
    ) {
        List<GithubRepoTechStackResponse> response = gitHubRepositoryService.getTechStack(owner, repo);
        return ApiResult.success(SuccessCode.SUCCESS, response);
    }

    @Operation(
            summary = "GitHub 저장소 파싱 결과 조회(DB 캐시 우선)",
            description = "owner/repo를 입력받아 저장된 파싱 캐시가 있으면 DB에서 반환하고, 없으면 GitHub API로 파싱하여 캐시에 저장 후 반환합니다."
    )
    @GetMapping("/{owner}/{repo}/parse-result")
    public ResponseEntity<ApiResult<ParsedRepositoryResponse>> getParsedRepository(
            HttpServletRequest httpServletRequest,
            @PathVariable String owner,
            @PathVariable String repo
    ) {
        Long appuserId = (Long) httpServletRequest.getAttribute("appuserId");
        String jobId = owner + "/" + repo;
        ParsedRepositoryResponse response;
        if (gitHubRepositoryParseService.hasFreshParseCache(owner, repo)) {
            response = gitHubRepositoryParseService.getParsedFromCache(owner, repo);
        } else if (jobManager.isRunning(jobId)) {
            log.warn("parse-result request skipped: generation is already running for owner={} repo={}", owner, repo);
            return ApiResult.error(ErrorCode.FAIL);
        } else {
            response = gitHubRepositoryParseService.parse(appuserId, owner, repo);
        }
        return ApiResult.success(SuccessCode.SUCCESS, response);
    }

    @Operation(
            summary = "GitHub 파일 내용 조회",
            description = "public 저장소의 path 필수, ref 선택 값으로 단일 파일 내용을 조회합니다. 디렉터리 path는 GITHUB_CONTENT_IS_DIRECTORY 오류로 처리합니다."
    )
    @GetMapping("/repositories/{owner}/{repo}/contents")
    public ResponseEntity<ApiResult<GitHubFileContentResponse>> getFileContent(
            HttpServletRequest httpServletRequest,
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam String path,
            @RequestParam(required = false) String ref
    ) {
        Long appuserId = (Long) httpServletRequest.getAttribute("appuserId");
        GitHubFileContentResponse response = gitHubRepositoryParseService.getFileContent(appuserId, owner, repo, path, ref);
        return ApiResult.success(SuccessCode.SUCCESS, response);
    }

    @Operation(
            summary = "GitHub 파일 검색",
            description = "public 저장소의 query 필수, ref 선택 값으로 파일명을 검색합니다. query에 /가 있으면 정확한 path로, 없으면 파일명으로 검색합니다. 후보가 정확히 하나이면 파일 내용도 함께 반환합니다."
    )
    @GetMapping("/repositories/{owner}/{repo}/files/search")
    public ResponseEntity<ApiResult<GitHubFileSearchResponse>> searchFiles(
            HttpServletRequest httpServletRequest,
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam String query,
            @RequestParam(required = false) String ref
    ) {
        Long appuserId = (Long) httpServletRequest.getAttribute("appuserId");
        GitHubFileSearchResponse response = gitHubRepositoryParseService.searchFiles(appuserId, owner, repo, query, ref);
        return ApiResult.success(SuccessCode.SUCCESS, response);
    }
}
