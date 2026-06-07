package com.ssafy.githubble.domain.github.service;

import com.ssafy.githubble.domain.github.client.GitDiagramClient;
import com.ssafy.githubble.domain.github.client.GitHubApiClient;
import com.ssafy.githubble.domain.github.domain.GitHubRepositorySummary;
import com.ssafy.githubble.domain.github.domain.GitHubRepositoryEntity;
import com.ssafy.githubble.domain.github.domain.GitHubRepositoryTechStackMapping;
import com.ssafy.githubble.domain.github.domain.enums.GenerationJobEvent;
import com.ssafy.githubble.domain.github.domain.enums.GenerationJobStep;
import com.ssafy.githubble.domain.github.dto.external.GitDiagramStreamEvent;
import com.ssafy.githubble.domain.github.dto.external.GitHubBranchApiResponse;
import com.ssafy.githubble.domain.github.dto.external.GitHubRepositoryApiResponse;
import com.ssafy.githubble.domain.github.repository.GitHubRepositorySummaryRepository;
import com.ssafy.githubble.domain.github.repository.GitHubRepositoryTechStackMappingRepository;
import com.ssafy.githubble.domain.github.repository.GitHubRepositoryRepository;
import com.ssafy.githubble.domain.github.support.GithubAccessTokenProvider;
import com.ssafy.githubble.domain.profile.domain.TechStack;
import com.ssafy.githubble.domain.profile.repository.TechStackRepository;
import com.ssafy.githubble.global.code.ErrorCode;
import com.ssafy.githubble.global.etc.JobManager;
import com.ssafy.githubble.global.exception.BusinessException;
import com.ssafy.githubble.global.properties.S3Properties;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubRepositoryGenerationService {

    private final GitHubApiClient gitHubApiClient;
    private final GitDiagramClient gitDiagramClient;
    private final GithubAccessTokenProvider githubAccessTokenProvider;

    private final GitHubRepositoryParseService gitHubRepositoryParseService;
    private final JobManager jobManager;

    private final GitHubRepositorySummaryRepository gitHubRepositorySummaryRepository;
    private final GitHubRepositoryRepository gitHubRepositoryRepository;
    private final GitHubRepositoryTechStackMappingRepository gitHubRepositoryTechStackMappingRepository;
    private final TechStackRepository techStackRepository;

    private final S3Client s3Client;
    private final S3Properties s3Properties;

    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public SseEmitter subscribeSSE(String jobId, SseEmitter emitter){
        jobManager.register(jobId, emitter);

        emitter.onCompletion(() -> {
            jobManager.remove(jobId, emitter);
        });

        emitter.onTimeout(() -> {
            jobManager.remove(jobId, emitter);
            emitter.complete();
        });

        emitter.onError(e -> {
            jobManager.remove(jobId, emitter);
        });

        return emitter;
    }

    public void generate(Long appuserId, String owner, String repo) {
        String jobId = owner + "/" + repo;
        if(!jobManager.startGeneration(jobId)) {
            log.info("generation.event event=generation_already_running eventName={} ownerRepo={} owner={} repo={} appuserId={}",
                    "이미 실행 중인 생성 작업", jobId, owner, repo, appuserId);
            return;
        }
        String generationRunId = UUID.randomUUID().toString();
        executor.execute(() -> {
            long startedAt = System.currentTimeMillis();
            AtomicReference<String> currentStage = new AtomicReference<>("generation_started");
            String commitSha = null;
            try {
                log.info("generation.event event=generation_started eventName={} ownerRepo={} generationRunId={} owner={} repo={} appuserId={}",
                        "생성 작업 시작", jobId, generationRunId, owner, repo, appuserId);

                currentStage.set("resolve_commit_sha");
                long stageStartedAt = System.currentTimeMillis();
                logStageStarted(jobId, generationRunId, owner, repo, appuserId, null, currentStage.get());
                commitSha = getDefaultBranchLatestCommitSha(appuserId, owner, repo);
                logStageCompleted(jobId, generationRunId, owner, repo, appuserId, commitSha, currentStage.get(), stageStartedAt);

                currentStage.set("load_github_token");
                stageStartedAt = System.currentTimeMillis();
                logStageStarted(jobId, generationRunId, owner, repo, appuserId, commitSha, currentStage.get());
                String githubAccessToken = githubAccessTokenProvider.getGithubAccessToken(appuserId);
                log.info("generation.event event=generation_stage_completed eventName={} ownerRepo={} generationRunId={} owner={} repo={} appuserId={} commitSha={} stage={} stageName={} tokenPresent={} elapsedMs={}",
                        "생성 단계 완료",
                        jobId,
                        generationRunId,
                        owner,
                        repo,
                        appuserId,
                        commitSha,
                        currentStage.get(),
                        stageName(currentStage.get()),
                        githubAccessToken != null && !githubAccessToken.isBlank(),
                        System.currentTimeMillis() - stageStartedAt);

                runSequential(appuserId, owner, repo, commitSha, jobId, generationRunId, githubAccessToken, currentStage);
                currentStage.set("job_complete");
                if(jobManager.completeCheck(jobId)){
                    jobManager.send(jobId, GenerationJobEvent.JOB_COMPLETED, "전체 작업 완료");
                    jobManager.complete(jobId);
                }
                log.info("generation.event event=generation_completed eventName={} ownerRepo={} generationRunId={} owner={} repo={} appuserId={} commitSha={} elapsedMs={}",
                        "생성 작업 완료", jobId, generationRunId, owner, repo, appuserId, commitSha, System.currentTimeMillis() - startedAt);
            } catch (Exception e) {
                Throwable cause = e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
                log.error("generation.event event=generation_failed eventName={} ownerRepo={} generationRunId={} owner={} repo={} appuserId={} commitSha={} stage={} stageName={} errorType={} elapsedMs={}",
                        "생성 작업 실패",
                        jobId,
                        generationRunId,
                        owner,
                        repo,
                        appuserId,
                        commitSha,
                        currentStage.get(),
                        stageName(currentStage.get()),
                        cause.getClass().getName(),
                        System.currentTimeMillis() - startedAt);
                jobManager.send(jobId, GenerationJobEvent.ERROR_OCCURRED, "작업 중 오류가 발생하여 비정상 종료합니다.");
                jobManager.complete(jobId);
            }
        });
    }

    // 아직 사용 안함, 나중에 성능개선용
    private void runParallel(Long appuserId, String owner, String repo, String commitSha, String jobId,
                             String githubAccessToken) {
        CompletableFuture<Void> parseFuture = CompletableFuture.runAsync(() -> {
            //gitHubRepositoryParseService.parse(appuserId, owner, repo);
            // TODO: 허니의 코드
        }, executor);

        CompletableFuture<Void> aiFuture = CompletableFuture.runAsync(() -> {
            List<GitDiagramStreamEvent> events = gitDiagramClient
                    .requestSetup(owner, repo, githubAccessToken, commitSha);
            String explanation = extractExplanation(events);
            List<String> techStacks = extractTechStacks(events);
            // TODO: TechStack 작업 완료되면 팔로업
            // TODO: explanation, techStacks 저장 로직에 전달
        }, executor);

        CompletableFuture.allOf(parseFuture, aiFuture).join();
    }

    private void runSequential(Long appuserId, String owner, String repo, String commitSha, String jobId,
                               String generationRunId, String githubAccessToken, AtomicReference<String> currentStage) {
        currentStage.set("parsing");
        long stageStartedAt = System.currentTimeMillis();
        logStageStarted(jobId, generationRunId, owner, repo, appuserId, commitSha, currentStage.get());
        jobManager.send(jobId, GenerationJobEvent.STEP_STARTED, "github 파싱 작업 시작");
        GitHubRepositoryEntity gitHubRepositoryEntity;
        try {
            if (gitHubRepositoryParseService.hasFreshParseCache(owner, repo)) {
                log.info("generation.event event=parsing_cache_hit eventName={} ownerRepo={} owner={} repo={} appuserId={}",
                        "기존 파싱 캐시 사용", jobId, owner, repo, appuserId);
            } else {
                gitHubRepositoryParseService.parse(appuserId, owner, repo);
            }
            gitHubRepositoryEntity = gitHubRepositoryRepository
                    .findByOwnerLoginIgnoreCaseAndRepoNameIgnoreCase(owner, repo)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "정보가 존재하지 않는 레포지토리입니다."));
            jobManager.jobCompleted(
                    jobId, GenerationJobEvent.STEP_COMPLETED, GenerationJobStep.PARSING);
            logStageCompleted(jobId, generationRunId, owner, repo, appuserId, commitSha, currentStage.get(), stageStartedAt);
        } catch (RuntimeException e) {
            logStageFailed(jobId, generationRunId, owner, repo, appuserId, commitSha, currentStage.get(), stageStartedAt, e);
            throw e;
        }

        Optional<GitHubRepositorySummary> summary = gitHubRepositorySummaryRepository
                .findByRepositoryRepoId(gitHubRepositoryEntity.getRepoId());
        jobManager.send(jobId, GenerationJobEvent.STEP_STARTED, "다이어그램 생성 작업 시작");
        jobManager.send(jobId, GenerationJobEvent.STEP_STARTED, "설명 생성 작업 시작");
        jobManager.send(jobId, GenerationJobEvent.STEP_STARTED, "테크 스택 생성 작업 시작");
        if(summary.isPresent()){
            if (isS3DiagramValid(owner, repo)) {
                currentStage.set("summary_cache_hit");
                log.info("generation.event event=generation_stage_completed eventName={} ownerRepo={} generationRunId={} owner={} repo={} appuserId={} commitSha={} stage={} stageName={} repoId={} reason={}",
                        "생성 단계 완료",
                        jobId,
                        generationRunId,
                        owner,
                        repo,
                        appuserId,
                        commitSha,
                        currentStage.get(),
                        stageName(currentStage.get()),
                        gitHubRepositoryEntity.getRepoId(),
                        "이미 저장된 요약 정보가 있어 GitDiagram 생성 요청을 건너뜁니다.");
                jobManager.jobCompleted(
                        jobId, GenerationJobEvent.STEP_COMPLETED, GenerationJobStep.DIAGRAM);
                jobManager.jobCompleted(
                        jobId, GenerationJobEvent.STEP_COMPLETED, GenerationJobStep.EXPLANATION);
                jobManager.jobCompleted(
                        jobId, GenerationJobEvent.STEP_COMPLETED, GenerationJobStep.RAG);
                jobManager.jobCompleted(
                        jobId, GenerationJobEvent.STEP_COMPLETED, GenerationJobStep.TECH_STACK);
                return;
            } else {
                log.info("generation.event event=summary_cache_invalidated eventName={} ownerRepo={} generationRunId={} owner={} repo={} appuserId={} commitSha={} stage={} stageName={} repoId={} reason={}",
                        "S3 다이어그램 파일 누락으로 캐시 무효화, GitDiagram 재생성 진행",
                        jobId,
                        generationRunId,
                        owner,
                        repo,
                        appuserId,
                        commitSha,
                        currentStage.get(),
                        stageName(currentStage.get()),
                        gitHubRepositoryEntity.getRepoId(),
                        "요약 정보는 있으나 S3 다이어그램 파일이 존재하지 않거나 읽을 수 없어 GitDiagram 생성을 다시 시도합니다.");
            }
        }

        currentStage.set("gitdiagram_request");
        stageStartedAt = System.currentTimeMillis();
        logStageStarted(jobId, generationRunId, owner, repo, appuserId, commitSha, currentStage.get());
        List<GitDiagramStreamEvent> events;
        try {
            events = gitDiagramClient.requestSetup(owner, repo, githubAccessToken, commitSha, jobId, generationRunId);
            log.info(
                    "generation.event event=generation_stage_completed eventName={} ownerRepo={} generationRunId={} owner={} repo={} appuserId={} commitSha={} stage={} stageName={} eventCount={} statuses={} elapsedMs={}",
                    "생성 단계 완료",
                    jobId,
                    generationRunId,
                    owner,
                    repo,
                    appuserId,
                    commitSha,
                    currentStage.get(),
                    stageName(currentStage.get()),
                    events.size(),
                    events.stream().map(GitDiagramStreamEvent::status).toList(),
                    System.currentTimeMillis() - stageStartedAt
            );
            jobManager.jobCompleted(
                    jobId, GenerationJobEvent.STEP_COMPLETED, GenerationJobStep.DIAGRAM);
        } catch (RuntimeException e) {
            logStageFailed(jobId, generationRunId, owner, repo, appuserId, commitSha, currentStage.get(), stageStartedAt, e);
            throw e;
        }

        currentStage.set("explanation_extract");
        stageStartedAt = System.currentTimeMillis();
        logStageStarted(jobId, generationRunId, owner, repo, appuserId, commitSha, currentStage.get());
        String explanation;
        try {
            explanation = extractExplanation(events);
            log.info(
                    "generation.event event=generation_stage_completed eventName={} ownerRepo={} generationRunId={} owner={} repo={} appuserId={} commitSha={} stage={} stageName={} explanationPresent={} explanationLength={} elapsedMs={}",
                    "생성 단계 완료",
                    jobId,
                    generationRunId,
                    owner,
                    repo,
                    appuserId,
                    commitSha,
                    currentStage.get(),
                    stageName(currentStage.get()),
                    explanation != null && !explanation.isBlank(),
                    explanation == null ? 0 : explanation.length(),
                    System.currentTimeMillis() - stageStartedAt
            );
        } catch (RuntimeException e) {
            logStageFailed(jobId, generationRunId, owner, repo, appuserId, commitSha, currentStage.get(), stageStartedAt, e);
            throw e;
        }

        currentStage.set("explanation_save");
        stageStartedAt = System.currentTimeMillis();
        logStageStarted(jobId, generationRunId, owner, repo, appuserId, commitSha, currentStage.get());
        try {
            saveExplanation(gitHubRepositoryEntity, explanation);
            jobManager.jobCompleted(
                    jobId, GenerationJobEvent.STEP_COMPLETED, GenerationJobStep.EXPLANATION);
            logStageCompleted(jobId, generationRunId, owner, repo, appuserId, commitSha, currentStage.get(), stageStartedAt);
        } catch (RuntimeException e) {
            logStageFailed(jobId, generationRunId, owner, repo, appuserId, commitSha, currentStage.get(), stageStartedAt, e);
            throw e;
        }

        currentStage.set("tech_stack_extract");
        stageStartedAt = System.currentTimeMillis();
        logStageStarted(jobId, generationRunId, owner, repo, appuserId, commitSha, currentStage.get());
        List<String> techStacks;
        try {
            techStacks = extractTechStacks(events);
            log.info(
                    "generation.event event=generation_stage_completed eventName={} ownerRepo={} generationRunId={} owner={} repo={} appuserId={} commitSha={} stage={} stageName={} techStackCount={} techStacks={} elapsedMs={}",
                    "생성 단계 완료",
                    jobId,
                    generationRunId,
                    owner,
                    repo,
                    appuserId,
                    commitSha,
                    currentStage.get(),
                    stageName(currentStage.get()),
                    techStacks.size(),
                    techStacks,
                    System.currentTimeMillis() - stageStartedAt
            );
        } catch (RuntimeException e) {
            logStageFailed(jobId, generationRunId, owner, repo, appuserId, commitSha, currentStage.get(), stageStartedAt, e);
            throw e;
        }

        currentStage.set("tech_stack_save");
        stageStartedAt = System.currentTimeMillis();
        logStageStarted(jobId, generationRunId, owner, repo, appuserId, commitSha, currentStage.get());
        try {
            saveTechStacks(gitHubRepositoryEntity, techStacks);
            jobManager.jobCompleted(
                    jobId, GenerationJobEvent.STEP_COMPLETED, GenerationJobStep.TECH_STACK);
            logStageCompleted(jobId, generationRunId, owner, repo, appuserId, commitSha, currentStage.get(), stageStartedAt);
        } catch (RuntimeException e) {
            logStageFailed(jobId, generationRunId, owner, repo, appuserId, commitSha, currentStage.get(), stageStartedAt, e);
            throw e;
        }
    }

    private void logStageStarted(String jobId, String generationRunId, String owner, String repo, Long appuserId, String commitSha, String stage) {
        log.info("generation.event event=generation_stage_started eventName={} ownerRepo={} generationRunId={} owner={} repo={} appuserId={} commitSha={} stage={} stageName={}",
                "생성 단계 시작", jobId, generationRunId, owner, repo, appuserId, commitSha, stage, stageName(stage));
    }

    private void logStageCompleted(String jobId, String generationRunId, String owner, String repo, Long appuserId, String commitSha,
                                   String stage, long stageStartedAt) {
        log.info("generation.event event=generation_stage_completed eventName={} ownerRepo={} generationRunId={} owner={} repo={} appuserId={} commitSha={} stage={} stageName={} elapsedMs={}",
                "생성 단계 완료",
                jobId,
                generationRunId,
                owner,
                repo,
                appuserId,
                commitSha,
                stage,
                stageName(stage),
                System.currentTimeMillis() - stageStartedAt);
    }

    private void logStageFailed(String jobId, String generationRunId, String owner, String repo, Long appuserId, String commitSha,
                                String stage, long stageStartedAt, RuntimeException e) {
        log.error("generation.event event=generation_stage_failed eventName={} ownerRepo={} generationRunId={} owner={} repo={} appuserId={} commitSha={} stage={} stageName={} errorType={} elapsedMs={}",
                "생성 단계 실패",
                jobId,
                generationRunId,
                owner,
                repo,
                appuserId,
                commitSha,
                stage,
                stageName(stage),
                e.getClass().getName(),
                System.currentTimeMillis() - stageStartedAt);
    }

    private String stageName(String stage) {
        return switch (stage) {
            case "generation_started" -> "생성 작업 시작";
            case "resolve_commit_sha" -> "기본 브랜치 최신 커밋 조회";
            case "load_github_token" -> "GitHub 토큰 조회";
            case "parsing" -> "GitHub 저장소 파싱";
            case "summary_cache_hit" -> "기존 요약 사용";
            case "gitdiagram_request" -> "GitDiagram 생성 요청";
            case "explanation_extract" -> "설명 결과 추출";
            case "explanation_save" -> "설명 저장";
            case "tech_stack_extract" -> "기술 스택 결과 추출";
            case "tech_stack_save" -> "기술 스택 저장";
            case "job_complete" -> "생성 작업 완료";
            default -> "알 수 없는 단계";
        };
    }

    private void saveExplanation(GitHubRepositoryEntity repository, String explanation) {
        log.info(
                "Persisting repository explanation: repoId={}, explanationPresent={}, explanationLength={}, reason={}",
                repository.getRepoId(),
                explanation != null && !explanation.isBlank(),
                explanation == null ? 0 : explanation.length(),
                "Tracing existing summary upsert input without changing null/blank handling"
        );
        GitHubRepositorySummary summary = gitHubRepositorySummaryRepository
                .findFirstByRepositoryRepoIdOrderByCreatedAtDesc(repository.getRepoId())
                .map(existingSummary -> {
                    existingSummary.updateExplanation(explanation);
                    return existingSummary;
                })
                .orElseGet(() -> new GitHubRepositorySummary(repository, explanation));

        gitHubRepositorySummaryRepository.save(summary);
    }

    private String extractExplanation(List<GitDiagramStreamEvent> events) {
        return events.stream()
                .filter(event -> "explanation".equals(event.status()))
                .map(GitDiagramStreamEvent::data)
                .filter(data -> data != null)
                .map(data -> data.get("explanation"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .findFirst()
                .orElse(null);
    }

    private List<String> extractTechStacks(List<GitDiagramStreamEvent> events) {
        return events.stream()
                .filter(event -> "tech_stack".equals(event.status()))
                .map(GitDiagramStreamEvent::data)
                .filter(data -> data != null)
                .map(data -> data.get("tech_stacks"))
                .filter(List.class::isInstance)
                .map(List.class::cast)
                .findFirst()
                .map(this::toStringList)
                .orElseGet(List::of);
    }

    private void saveTechStacks(GitHubRepositoryEntity repository, List<String> techStackNames) {
        List<String> distinctTechStackNames = techStackNames.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .distinct()
                .toList();

        // 넣을 테크 스택들의 ID를 테이블에서 가져와 id를 set으로 저장
        List<TechStack> receivedTechStacks = techStackRepository.findAllByNameIn(distinctTechStackNames);
        log.info(
                "Resolving tech stack mappings: repoId={}, requestedCount={}, distinctCount={}, resolvedCount={}, requestedNames={}, reason={}",
                repository.getRepoId(),
                techStackNames.size(),
                distinctTechStackNames.size(),
                receivedTechStacks.size(),
                distinctTechStackNames,
                "Tracing existing tech stack mapping upsert inputs without changing mapping behavior"
        );
        Set<Long> receivedTechStackIds = receivedTechStacks.stream()
                .map(TechStack::getTechstackId)
                .collect(Collectors.toSet());

        // 이미 존재하는 매핑 정보를 리스트로 가져온 뒤, id 들은 집합으로 변경
        List<GitHubRepositoryTechStackMapping> existingMappings =
                gitHubRepositoryTechStackMappingRepository.findByRepositoryRepoId(repository.getRepoId());
        Set<Long> existingTechStackIds = existingMappings.stream()
                .map(mapping -> mapping.getTechStack().getTechstackId())
                .collect(Collectors.toSet());

        // 지워야할 매핑 정보 저장 (넣을 스택 id set 안에 기존 매핑 정보의 id가 없으면 저장)
        List<GitHubRepositoryTechStackMapping> mappingsToDelete = existingMappings.stream()
                .filter(mapping -> !receivedTechStackIds.contains(mapping.getTechStack().getTechstackId()))
                .toList();
        // 넣을 매핑 정보 저장 (새로 받은 스택 id들이 기존 매핑 정보 id 셋에 없으면 저장)
        List<GitHubRepositoryTechStackMapping> mappingsToSave = receivedTechStacks.stream()
                .filter(techStack -> !existingTechStackIds.contains(techStack.getTechstackId()))
                .map(techStack -> new GitHubRepositoryTechStackMapping(repository, techStack))
                .toList();

        log.info(
                "Applying tech stack mapping changes: repoId={}, existingCount={}, deleteCount={}, saveCount={}, reason={}",
                repository.getRepoId(),
                existingMappings.size(),
                mappingsToDelete.size(),
                mappingsToSave.size(),
                "Tracing existing delete/save counts before repository mapping persistence"
        );
        gitHubRepositoryTechStackMappingRepository.deleteAll(mappingsToDelete);
        gitHubRepositoryTechStackMappingRepository.saveAll(mappingsToSave);
    }

    private List<String> toStringList(List<?> values) {
        List<String> result = new ArrayList<>();
        for (Object value : values) {
            if (value != null) {
                result.add(value.toString());
            }
        }
        return result;
    }

    private String getDefaultBranchLatestCommitSha(Long appuserId, String owner, String repo) {
        String githubAccessToken = githubAccessTokenProvider.getGithubAccessToken(appuserId);
        GitHubRepositoryApiResponse repository = gitHubApiClient.getRepository(githubAccessToken, owner, repo);
        GitHubBranchApiResponse branch = gitHubApiClient.getBranch(
                githubAccessToken,
                owner,
                repo,
                repository.defaultBranch()
        );
        if (branch == null || branch.commit() == null || branch.commit().sha() == null || branch.commit().sha().isBlank()) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "기본 브랜치의 최신 커밋 SHA를 찾을 수 없습니다.");
        }
        return branch.commit().sha();
    }

    private boolean isS3DiagramValid(String owner, String repo) {
        String key = s3Properties.getArtifactPrefix() + "/" + owner + "/" + repo + ".json";
        try {
            s3Client.headObject(software.amazon.awssdk.services.s3.model.HeadObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(key)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            log.info("S3 diagram not found for {}/{}: key={}", owner, repo, key);
            return false;
        } catch (SdkException e) {
            log.warn("S3 head failed for {}/{}: key={}, errorType={}", owner, repo, key, e.getClass().getName());
            return false;
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
