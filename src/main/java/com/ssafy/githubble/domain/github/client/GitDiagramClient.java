package com.ssafy.githubble.domain.github.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.githubble.domain.github.dto.external.GitDiagramStreamEvent;
import com.ssafy.githubble.global.code.ErrorCode;
import com.ssafy.githubble.global.exception.BusinessException;
import com.ssafy.githubble.global.properties.AiServerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

@Component
@Slf4j
public class GitDiagramClient {

    private static final Set<String> STREAM_STATUSES = Set.of("explanation", "tech_stack", "diagram", "complete", "error");
    private static final Pattern ERROR_CODE_PATTERN = Pattern.compile(
            "(?i)\\\"(?:error_code|errorCode|code)\\\"\\s*:\\s*\\\"([^\\\"]+)\\\""
    );

    private final AiServerProperties aiServerProperties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public GitDiagramClient(ObjectMapper objectMapper, AiServerProperties aiServerProperties) {
        this.objectMapper = objectMapper;
        this.aiServerProperties = aiServerProperties;
        this.webClient = WebClient.builder()
                .baseUrl(aiServerProperties.getBaseUrl())
                .build();
    }

    public List<GitDiagramStreamEvent> requestSetup(String owner,
                                                    String repo,
                                                    String githubAccessToken,
                                                    String commitSha) {
        return requestSetup(owner, repo, githubAccessToken, commitSha, owner + "/" + repo, null, event -> {
        });
    }

    public List<GitDiagramStreamEvent> requestSetup(String owner,
                                                    String repo,
                                                    String githubAccessToken,
                                                    String commitSha,
                                                    String jobId) {
        return requestSetup(owner, repo, githubAccessToken, commitSha, jobId, null, event -> {
        });
    }

    public List<GitDiagramStreamEvent> requestSetup(String owner,
                                                    String repo,
                                                    String githubAccessToken,
                                                    String commitSha,
                                                    String jobId,
                                                    String generationRunId) {
        return requestSetup(owner, repo, githubAccessToken, commitSha, jobId, generationRunId, event -> {
        });
    }

    public List<GitDiagramStreamEvent> requestSetup(String owner,
                                                    String repo,
                                                    String githubAccessToken,
                                                    String commitSha,
                                                    Consumer<GitDiagramStreamEvent> eventConsumer) {
        return requestSetup(owner, repo, githubAccessToken, commitSha, owner + "/" + repo, null, eventConsumer);
    }

    public List<GitDiagramStreamEvent> requestSetup(String owner,
                                                    String repo,
                                                    String githubAccessToken,
                                                    String commitSha,
                                                    String jobId,
                                                    String generationRunId,
                                                    Consumer<GitDiagramStreamEvent> eventConsumer) {
        try {
            log.info(
                    "gitdiagram.event event=request_prepared eventName={} baseUrl={} path={} owner={} repo={} commitSha={} ownerRepo={} generationRunId={} reason={}",
                    "GitDiagram 요청 준비",
                    aiServerProperties.getBaseUrl(),
                    aiServerProperties.getSetupPath(),
                    owner,
                    repo,
                    commitSha,
                    jobId,
                    generationRunId,
                    "외부 AI 생성 요청 경계를 추적합니다. 인증 정보는 로그에 남기지 않습니다."
            );
            List<GitDiagramStreamEvent> events = webClient.post()
                    .uri(aiServerProperties.getSetupPath())
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .bodyValue(new GitDiagramGenerateRequest(owner, repo, githubAccessToken, commitSha, jobId, generationRunId))
                    .retrieve()
                    .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {
                    })
                    .doOnSubscribe(subscription -> log.info(
                            "gitdiagram.event event=stream_subscribed eventName={} baseUrl={} path={} owner={} repo={} commitSha={} ownerRepo={} generationRunId={}",
                            "GitDiagram SSE 구독 시작",
                            aiServerProperties.getBaseUrl(),
                            aiServerProperties.getSetupPath(),
                            owner,
                            repo,
                            commitSha,
                            jobId,
                            generationRunId
                    ))
                    .flatMap(serverSentEvent -> {
                        String data = serverSentEvent.data();
                        if (data == null || data.isBlank()) {
                            return reactor.core.publisher.Mono.<String>empty();
                        }
                        log.info("gitdiagram.event event=stream_raw_received eventName={} owner={} repo={} commitSha={} ownerRepo={} generationRunId={} payloadLength={}",
                                "GitDiagram SSE 원문 수신", owner, repo, commitSha, jobId, generationRunId, data.length());
                        return reactor.core.publisher.Mono.just(data);
                    })
                    .filter(data -> isJsonPayload(data, generationRunId))
                    .map(data -> parseStreamEvent(data, generationRunId))
                    .filter(event -> isSupportedStatus(event, generationRunId))
                    .doOnNext(event -> logStreamEvent(owner, repo, commitSha, jobId, generationRunId, event))
                    .map(event -> throwIfErrorEvent(owner, repo, commitSha, jobId, generationRunId, event))
                    .doOnNext(eventConsumer)
                    .takeUntil(event -> "complete".equals(event.status()))
                    .doOnComplete(() -> log.info("gitdiagram.event event=stream_completed eventName={} owner={} repo={} commitSha={} ownerRepo={} generationRunId={}",
                            "GitDiagram SSE 스트림 완료", owner, repo, commitSha, jobId, generationRunId))
                    .doOnError(error -> log.warn("gitdiagram.event event=stream_failed eventName={} owner={} repo={} commitSha={} ownerRepo={} generationRunId={} errorType={}",
                            "GitDiagram SSE 스트림 실패", owner, repo, commitSha, jobId, generationRunId, error.getClass().getName()))
                    .collectList()
                    .block();
            List<GitDiagramStreamEvent> result = events == null ? List.of() : events;
            log.info(
                    "gitdiagram.event event=request_finished eventName={} owner={} repo={} commitSha={} ownerRepo={} generationRunId={} eventCount={} statuses={} reason={}",
                    "GitDiagram 요청 완료",
                    owner,
                    repo,
                    commitSha,
                    jobId,
                    generationRunId,
                    result.size(),
                    result.stream().map(GitDiagramStreamEvent::status).toList(),
                    "AI stream이 저장 전 필요한 이벤트를 생성했는지 추적합니다."
            );
            return result;
        } catch (WebClientResponseException e) {
            HttpStatusCode statusCode = e.getStatusCode();
            HttpFailureResponseSummary responseSummary = summarizeHttpFailureResponse(e.getResponseBodyAsString());
            log.error(
                    "gitdiagram.event event=http_request_failed eventName={} baseUrl={} path={} owner={} repo={} commitSha={} ownerRepo={} generationRunId={} status={} errorCode={} bodyLength={}",
                    "GitDiagram HTTP 요청 실패",
                    aiServerProperties.getBaseUrl(),
                    aiServerProperties.getSetupPath(),
                    owner,
                    repo,
                    commitSha,
                    jobId,
                    generationRunId,
                    statusCode,
                    responseSummary.errorCode(),
                    responseSummary.bodyLength()
            );
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "외부 AI 서버 요청에 실패했습니다.");
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error(
                    "gitdiagram.event event=runtime_request_failed eventName={} baseUrl={} path={} owner={} repo={} commitSha={} ownerRepo={} generationRunId={} errorType={}",
                    "GitDiagram 요청 처리 실패",
                    aiServerProperties.getBaseUrl(),
                    aiServerProperties.getSetupPath(),
                    owner,
                    repo,
                    commitSha,
                    jobId,
                    generationRunId,
                    e.getClass().getName()
            );
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "외부 AI 서버 요청에 실패했습니다.");
        }
    }

    private boolean isJsonPayload(String data, String generationRunId) {
        boolean jsonPayload = data.trim().startsWith("{");
        if (!jsonPayload) {
            log.info("gitdiagram.event event=stream_data_ignored eventName={} generationRunId={} reason={} payloadLength={}",
                    "GitDiagram SSE 데이터 무시", generationRunId, "JSON 형식이 아닌 SSE 데이터입니다.", data.length());
        }
        return jsonPayload;
    }

    private boolean isSupportedStatus(GitDiagramStreamEvent event, String generationRunId) {
        boolean supportedStatus = STREAM_STATUSES.contains(event.status());
        if (!supportedStatus) {
            log.info("gitdiagram.event event=stream_event_ignored eventName={} generationRunId={} reason={} status={} statusName={} sessionId={} dataKeys={}",
                    "GitDiagram SSE 이벤트 무시",
                    generationRunId,
                    "지원하지 않는 상태값입니다.",
                    event.status(),
                    statusName(event.status()),
                    event.sessionId(),
                    dataKeys(event));
        }
        return supportedStatus;
    }

    private GitDiagramStreamEvent parseStreamEvent(String data, String generationRunId) {
        try {
            return objectMapper.readValue(data, GitDiagramStreamEvent.class);
        } catch (JsonProcessingException e) {
            log.warn("gitdiagram.event event=stream_data_parse_failed eventName={} generationRunId={} payloadLength={} reason={} errorType={}",
                    "GitDiagram SSE 파싱 실패",
                    generationRunId,
                    data.length(),
                    "GD/BE stream 계약 문제를 진단하기 위해 payload 길이만 남깁니다.",
                    e.getClass().getName());
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "외부 AI 서버 SSE 이벤트를 파싱하지 못했습니다.");
        }
    }

    private void logStreamEvent(String owner, String repo, String commitSha, String jobId, String generationRunId, GitDiagramStreamEvent event) {
        if ("error".equals(event.status())) {
            log.error("gitdiagram.event event=stream_error eventName={} owner={} repo={} commitSha={} ownerRepo={} generationRunId={} sessionId={} errorCode={} failureStage={} failureStageName={} validationErrorLength={} errorLength={}",
                    "GitDiagram 오류 이벤트 수신",
                    owner,
                    repo,
                    commitSha,
                    jobId,
                    generationRunId,
                    event.sessionId(),
                    event.errorCode(),
                    event.failureStage(),
                    statusName(event.failureStage()),
                    safeLength(event.validationError()),
                    safeLength(event.error()));
            return;
        }
        log.info("gitdiagram.event event=stream_event eventName={} owner={} repo={} commitSha={} ownerRepo={} generationRunId={} status={} statusName={} sessionId={} dataKeys={}",
                "GitDiagram SSE 이벤트 수신",
                owner,
                repo,
                commitSha,
                jobId,
                generationRunId,
                event.status(),
                statusName(event.status()),
                event.sessionId(),
                dataKeys(event));
    }

    private GitDiagramStreamEvent throwIfErrorEvent(String owner, String repo, String commitSha, String jobId,
                                                    String generationRunId, GitDiagramStreamEvent event) {
        if (!"error".equals(event.status())) {
            return event;
        }
        String errorCode = event.errorCode() == null || event.errorCode().isBlank() ? "UNKNOWN" : event.errorCode();
        String failureStage = event.failureStage() == null || event.failureStage().isBlank() ? "unknown" : event.failureStage();
        log.error("gitdiagram.event event=stream_error_mapped eventName={} owner={} repo={} commitSha={} ownerRepo={} generationRunId={} sessionId={} errorCode={} failureStage={} failureStageName={} reason={}",
                "GitDiagram 오류를 BE 생성 실패로 변환",
                owner,
                repo,
                commitSha,
                jobId,
                generationRunId,
                event.sessionId(),
                errorCode,
                failureStage,
                statusName(failureStage),
                "GD가 status=error SSE를 반환하여 BE 생성 작업을 실패로 연결합니다.");
        throw new BusinessException(
                ErrorCode.EXTERNAL_API_ERROR,
                "GitDiagram 생성이 " + failureStage + " 단계에서 실패했습니다. 원인은 " + errorCode + "입니다."
        );
    }

    private String statusName(String status) {
        if (status == null || status.isBlank()) {
            return "상태 없음";
        }
        return switch (status) {
            case "explanation" -> "설명 생성 결과";
            case "tech_stack" -> "기술 스택 생성 결과";
            case "diagram" -> "다이어그램 생성 결과";
            case "complete" -> "GitDiagram 생성 완료";
            case "error" -> "GitDiagram 생성 실패";
            case "started" -> "생성 시작";
            case "graph_validating" -> "그래프 검증";
            case "diagram_compiling" -> "다이어그램 컴파일";
            default -> "알 수 없는 상태";
        };
    }

    private List<String> dataKeys(GitDiagramStreamEvent event) {
        if (event.data() == null || event.data().isEmpty()) {
            return List.of();
        }
        return List.copyOf(event.data().keySet());
    }

    static HttpFailureResponseSummary summarizeHttpFailureResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return new HttpFailureResponseSummary(0, "EMPTY_BODY");
        }

        String errorCode = extractFirstGroup(ERROR_CODE_PATTERN, responseBody, "HTTP_RESPONSE_BODY_PRESENT");
        return new HttpFailureResponseSummary(responseBody.length(), errorCode);
    }

    private static String extractFirstGroup(Pattern pattern, String value, String defaultValue) {
        java.util.regex.Matcher matcher = pattern.matcher(value);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return defaultValue;
    }

    static int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    record HttpFailureResponseSummary(
            int bodyLength,
            String errorCode
    ) {
    }

    record GitDiagramGenerateRequest(
            String username,
            String repo,
            @JsonProperty("github_pat") String githubPat,
            @JsonProperty("commit_sha") String commitSha,
            @JsonProperty("owner_repo") String ownerRepo,
            @JsonProperty("generation_run_id") String generationRunId
    ) {
    }

}
