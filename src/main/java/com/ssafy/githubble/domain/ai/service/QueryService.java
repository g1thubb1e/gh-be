package com.ssafy.githubble.domain.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.githubble.domain.ai.component.GraphRagAssistant;
import com.ssafy.githubble.domain.ai.component.QueryIntent;
import com.ssafy.githubble.domain.ai.component.RagJudgeAssistant;
import com.ssafy.githubble.domain.ai.component.RuleBasedQueryClassifier;
import com.ssafy.githubble.domain.ai.component.SourceAnalysisAssistant;
import com.ssafy.githubble.domain.ai.component.SourceTool;
import com.ssafy.githubble.domain.ai.component.StreamingGraphRagAssistant;
import com.ssafy.githubble.domain.ai.component.retrieve.GraphResult;
import com.ssafy.githubble.domain.ai.component.retrieve.GraphRetriever;
import com.ssafy.githubble.domain.ai.component.retrieve.HybridRetriever;
import com.ssafy.githubble.domain.ai.component.retrieve.VectorRetriever;
import com.ssafy.githubble.domain.ai.dto.SourceRef;
import com.ssafy.githubble.domain.ai.dto.request.QueryRequest;
import com.ssafy.githubble.domain.ai.dto.response.QueryResponse;
import com.ssafy.githubble.domain.ai.entity.AgentConversation;
import com.ssafy.githubble.domain.ai.entity.AgentMessageRole;
import com.ssafy.githubble.domain.ai.entity.QaEvalEntity;
import com.ssafy.githubble.domain.ai.repository.QaEvalRepository;
import com.ssafy.githubble.domain.github.domain.GitHubRepositoryEntity;
import com.ssafy.githubble.domain.github.repository.GitHubRepositoryRepository;
import com.ssafy.githubble.domain.github.service.GitHubRepositoryParseService;
import com.ssafy.githubble.global.code.ErrorCode;
import com.ssafy.githubble.global.code.SuccessCode;
import com.ssafy.githubble.global.util.LogSanitizer;
import com.ssafy.githubble.neo4j.entity.Architecture;
import com.ssafy.githubble.neo4j.repository.ArchitectureRepository;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryService {

    private final RuleBasedQueryClassifier classifier;
    private final RoutingService routingService;
    private final GraphRetriever graphRetriever;
    private final VectorRetriever vectorRetriever;
    private final HybridRetriever hybridRetriever;
    private final ChatModel chatLanguageModel;
    private final StreamingChatModel streamingChatModel;
    private final RagJudgeAssistant ragJudgeAssistant;
    private final GraphRagAssistant graphRagAssistant;
    private final QaEvalRepository qaEvalRepository;
    private final ArchitectureRepository architectureRepository;
    private final GitHubRepositoryRepository gitHubRepositoryRepository;
    private final ObjectMapper objectMapper;
    private final AgentConversationService conversationService;
    private final GitHubRepositoryParseService gitHubRepositoryParseService;
    private final SourceAnalysisAssistant sourceAnalysisAssistant;

    private static final Pattern SCORE_PATTERN = Pattern.compile("\\b(10|[1-9])\\b");
    private static final Pattern NODE_META_PATTERN = Pattern.compile(
            "\\s*<node-meta>\\s*(\\{.*?\\})\\s*</node-meta>\\s*$", Pattern.DOTALL);

    @Value("${graphrag.context.maxTokens:6000}")
    private int maxTokens;

    @Value("${graphrag.context.maxTokensLarge:20000}")
    private int maxTokensLarge;

    private final String TEST_ANSWER = """
            ### AI Agent 답변
            Graph Structure Logic은 AI 모델이 뽑아낸 초기 그래프 결과를 **정규화된 DiagramGraph 형태로 구조화**하고, 저장소 파일트리/참조 무결성 관점에서 **그래프 자체의 검증(노드·그룹·엣지·path 정합성)**을 수행한 뒤, 최종적으로 **Mermaid 다이어그램 문자열로 컴파일**하는 핵심 서비스입니다. 또한 생성된 Mermaid는 별도의 Mermaid 검증 컴포넌트로 전달되어 문법 검증을 받는 흐름으로 연결됩니다.
            
            ---
            
            ### 1️⃣ 핵심 요소 (최대 3개)
            
            | 그룹 | 핵심 요소 | 위치 | 핵심 역할 |
            |------|---------|------|---------|
            | - | Graph Structure Logic | `backend/app/services/graph_service.py` | AI 출력 그래프를 검증·정규화하고 Mermaid로 컴파일하는 핵심 로직 |
            
            ---
            
            ### 2️⃣ 핵심 흐름 (최대 3개)
            
            | 핵심 흐름 | 설명 |
            |---------|------|
            | [ai_model_provider] --Raw Output Data--> [Graph Structure Logic] | AI가 생성한 초기 구조(JSON/Mermaid 등)를 받아 최종 그래프 표현으로 구조화/정리 |
            | [Graph Structure Logic] --Code Validation--> [mermaid_validator] | 컴파일된 Mermaid 문법을 외부 검증기로 보내 유효성 검사를 수행 |
            
            ---
            
            ### 3️⃣ 소스코드 분석
            **참고한 소스 파일**: `backend/app/services/graph_service.py` \s
            소스 기준으로 Graph Structure Logic은 `validate_diagram_graph()`에서 **ID 중복, groupId 존재 여부, 엣지 from/to 참조 무결성, 노드 path가 실제 파일트리(lookup)와 일치하는지** 등을 검증합니다. 이후 `compile_diagram_graph()`가 **그룹별 subgraph 구성, 노드/엣지 렌더링, (path가 있으면) GitHub URL 클릭 링크 추가**까지 포함해 최종 Mermaid flowchart 문자열을 생성합니다. Mermaid 검증기 호출 자체는 이 파일에 직접 구현돼 있지 않고, 검증 피드백 문자열을 만들기 위한 포맷팅(`format_graph_validation_feedback()`) 중심으로 구성되어 있습니다.

            """;

    public QueryResponse answerWithConversation(Long appuserId, UUID conversationUuid, QueryRequest request, boolean testMode) {
        PreparedConversationQuery prepared = prepareConversationQuery(appuserId, conversationUuid, request, !testMode);

        String answer = TEST_ANSWER;
        NodeMetaResult meta = extractNodeMeta(answer);
        try {
            if (!testMode) {
                answer = graphRagAssistant.ask(prepared.wrappedContext(), request.getQuestion());
                meta = extractNodeMeta(answer);
            }
            conversationService.saveMessage(prepared.conversation(), AgentMessageRole.ASSISTANT, meta.answer(), null);
        } catch (RuntimeException e) {
            String fallback = "답변 생성 중 오류가 발생했습니다.";
            conversationService.saveMessage(prepared.conversation(), AgentMessageRole.ASSISTANT, fallback,
                    "errorType=" + e.getClass().getName());
            throw e;
        }

        return buildConversationQueryResponse(meta, request.getArchId(), prepared);
    }

    private QueryResponse buildConversationQueryResponse(
            NodeMetaResult meta,
            String requestedArchId,
            PreparedConversationQuery prepared
    ) {
        AgentConversation conversation = prepared.conversation();
        return QueryResponse.builder()
                .answer(meta.answer())
                .intent(prepared.intent())
                .topNode(meta.topNode())
                .keyNodes(meta.keyNodes())
                .truncated(prepared.truncated())
                .resolvedArchId(prepared.resolvedArchId().equals(requestedArchId) ? null : prepared.resolvedArchId())
                .conversationUuid(conversation.getConversationUuid())
                .repoUuid(findRepoUuid(conversation.getRepoId()))
                .build();
    }

    public QueryResponse answer(String question, String archId, boolean testMode) {
        String resolvedArchId = resolveArchId(archId);
        QueryIntent intent = routingService.classify(question);
        logAiQueryStarted("query_started", intent, resolvedArchId, question, null);

        ContextResult ctx = buildContext(intent, resolvedArchId, question);

        int tokenBudget = isLargeIntent(intent) ? maxTokensLarge : maxTokens;
        String wrappedContext = wrapAndTruncate(ctx.rawContext(), tokenBudget);
        boolean truncated = wrappedContext.endsWith("[TRUNCATED]");
        log.info("Context 조립 완료: intent={}, archId={}, contextChars={}, truncated={}",
                intent, resolvedArchId, ctx.rawContext().length(), truncated);

        String rawAnswer;
        if (testMode) {
            rawAnswer = TEST_ANSWER;
        } else {
            GraphRagAssistant toolAssistant = AiServices.builder(GraphRagAssistant.class)
                    .chatModel(chatLanguageModel)
                    .build();
            rawAnswer = toolAssistant.ask(wrappedContext, question);
        }
        NodeMetaResult meta = extractNodeMeta(rawAnswer);

        return QueryResponse.builder()
                .answer(meta.answer())
                .intent(intent)
                .topNode(meta.topNode())
                .keyNodes(meta.keyNodes())
                .truncated(truncated)
                .resolvedArchId(resolvedArchId.equals(archId) ? null : resolvedArchId)
                .build();
    }

    public QueryIntent route(String question) {
        return routingService.classify(question);
    }

    public QueryResponse answerVector(String question, String archId) {
        String resolvedArchId = resolveArchId(archId);
        QueryIntent intent = QueryIntent.FALLBACK;
        logAiQueryStarted("query_started", intent, resolvedArchId, question, null);

        ContextResult ctx = buildContext(intent, resolvedArchId, question);

        int tokenBudget = isLargeIntent(intent) ? maxTokensLarge : maxTokens;
        String wrappedContext = wrapAndTruncate(ctx.rawContext(), tokenBudget);
        boolean truncated = wrappedContext.endsWith("[TRUNCATED]");
        log.info("Context 조립 완료: intent={}, archId={}, contextChars={}, truncated={}",
                intent, resolvedArchId, ctx.rawContext().length(), truncated);

        GraphRagAssistant toolAssistant = AiServices.builder(GraphRagAssistant.class)
                .chatModel(chatLanguageModel)
                .build();
        String rawAnswer = toolAssistant.ask(wrappedContext, question);
        NodeMetaResult meta = extractNodeMeta(rawAnswer);

        return QueryResponse.builder()
                .answer(meta.answer())
                .intent(intent)
                .topNode(meta.topNode())
                .keyNodes(meta.keyNodes())
                .truncated(truncated)
                .resolvedArchId(resolvedArchId.equals(archId) ? null : resolvedArchId)
                .build();
    }

    public QueryResponse retrieve(String question, String archId) {
        String resolvedArchId = resolveArchId(archId);
        QueryIntent intent = classifier.classify(question);
        logAiQueryStarted("query_started", intent, resolvedArchId, question, null);

        ContextResult ctx = buildContext(intent, resolvedArchId, question);

        return QueryResponse.builder()
                .answer(ctx.rawContext())
                .intent(intent)
                .topNode(null)
                .keyNodes(List.of())
                .truncated(false)
                .resolvedArchId(resolvedArchId.equals(archId) ? null : resolvedArchId)
                .build();
    }

    public Flux<ServerSentEvent<String>> answerFlux(String question, String archId, boolean testMode, Long appuserId) {
        String resolvedArchId = resolveArchId(archId);
        QueryIntent intent = testMode ? QueryIntent.FALLBACK : routingService.classify(question);
        logAiQueryStarted("stream_query_started", intent, resolvedArchId, question, appuserId);

        ContextResult ctx = buildContext(intent, resolvedArchId, question);
        int tokenBudget = isLargeIntent(intent) ? maxTokensLarge : maxTokens;
        String wrappedContext = wrapAndTruncate(ctx.rawContext(), tokenBudget);
        boolean truncated = wrappedContext.endsWith("[TRUNCATED]");

        if (testMode) {
            NodeMetaResult testMeta = extractNodeMeta(TEST_ANSWER);
            List<String> chunks = new ArrayList<>();
            for (int i = 0; i < testMeta.answer().length(); i += 4) {
                chunks.add(testMeta.answer().substring(i, Math.min(i + 4, testMeta.answer().length())));
            }
            return Flux.fromIterable(chunks)
                    .map(this::toTokenEvent)
                    .delayElements(Duration.ofMillis(20))
                    .concatWith(Flux.just(toDoneEvent(intent, testMeta.topNode(), testMeta.keyNodes(), truncated)));
        }

        return streamAnswer(question, resolvedArchId, wrappedContext, appuserId,
                token -> toTokenEvent(token),
                meta -> toDoneEvent(intent, meta.topNode(), meta.keyNodes(), truncated),
                this::toErrorEvent);
    }

    public Flux<ServerSentEvent<String>> answerFluxWithConversation(Long appuserId, UUID conversationUuid, QueryRequest request) {
        PreparedConversationQuery prepared = prepareConversationQuery(appuserId, conversationUuid, request, true);

        StringBuilder answerBuffer = new StringBuilder();
        return streamAnswer(request.getQuestion(), prepared.resolvedArchId(), prepared.wrappedContext(), appuserId,
                token -> {
                    answerBuffer.append(token);
                    return toApiResultEvent("token", Map.of("type", "token", "content", token));
                },
                meta -> {
                    conversationService.saveMessage(prepared.conversation(), AgentMessageRole.ASSISTANT, meta.answer(), null);
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("type", "done");
                    data.put("conversationUuid", prepared.conversation().getConversationUuid());
                    data.put("repoUuid", findRepoUuid(prepared.conversation().getRepoId()));
                    data.put("intent", prepared.intent().name());
                    data.put("sources", prepared.context().sources());
                    data.put("topNode", meta.topNode());
                    data.put("keyNodes", meta.keyNodes() != null ? meta.keyNodes() : List.of());
                    data.put("truncated", prepared.truncated());
                    return toApiResultEvent("done", data);
                },
                throwable -> {
                    String fallback = answerBuffer.isEmpty()
                            ? "답변 생성 중 오류가 발생했습니다."
                            : answerBuffer + "\n\n[ERROR] 답변 생성 중 오류가 발생했습니다.";
                    conversationService.saveMessage(prepared.conversation(), AgentMessageRole.ASSISTANT,
                            fallback, "errorType=" + throwable.getClass().getName());
                    return toApiResultErrorEvent();
                });
    }

    private Flux<ServerSentEvent<String>> streamAnswer(
            String question,
            String resolvedArchId,
            String wrappedContext,
            Long appuserId,
            java.util.function.Function<String, ServerSentEvent<String>> tokenEventFactory,
            java.util.function.Function<NodeMetaResult, ServerSentEvent<String>> doneEventFactory,
            java.util.function.Function<Throwable, ServerSentEvent<String>> errorEventFactory
    ) {
        String[] parts = resolvedArchId.split("__", 2);
        String owner = parts[0];
        String repo = parts.length > 1 ? parts[1] : "";

        StreamingGraphRagAssistant toolAssistant;
        if (appuserId == null) {
            toolAssistant = AiServices.builder(StreamingGraphRagAssistant.class)
                    .streamingChatModel(streamingChatModel)
                    .build();
        } else {
            SourceTool sourceTool = new SourceTool(appuserId, owner, repo,
                    gitHubRepositoryParseService, sourceAnalysisAssistant);
            toolAssistant = AiServices.builder(StreamingGraphRagAssistant.class)
                    .streamingChatModel(streamingChatModel)
                    .tools(sourceTool)
                    .build();
        }

        StringBuilder buffer = new StringBuilder();
        int[] emittedUpTo = {0};

        return Flux.<ServerSentEvent<String>>create(sink -> {
            toolAssistant.askStream(wrappedContext, question)
                    .onPartialResponse(token -> {
                        if (sink.isCancelled()) {
                            return;
                        }
                        buffer.append(token);
                        String s = buffer.toString();
                        int metaStart = s.indexOf("<node-meta>");
                        int safeEnd = metaStart >= 0
                                ? metaStart
                                : Math.max(emittedUpTo[0], s.length() - "<node-meta>".length() + 1);
                        if (safeEnd > emittedUpTo[0]) {
                            sink.next(tokenEventFactory.apply(s.substring(emittedUpTo[0], safeEnd)));
                            emittedUpTo[0] = safeEnd;
                        }
                    })
                    .onCompleteResponse(response -> {
                        if (!sink.isCancelled()) {
                            NodeMetaResult meta = extractNodeMeta(buffer.toString());
                            sink.next(doneEventFactory.apply(meta));
                            sink.complete();
                        }
                    })
                    .onError(e -> {
                        log.error("ai.query.event event=stream_failed errorType={}", e.getClass().getName());
                        if (!sink.isCancelled()) {
                            sink.next(errorEventFactory.apply(e));
                            sink.complete();
                        }
                    })
                    .start();
        })
        .onErrorResume(e -> {
            log.error("ai.query.event event=stream_failed errorType={}", e.getClass().getName());
            return Flux.just(errorEventFactory.apply(e));
        })
        .subscribeOn(Schedulers.boundedElastic());
    }

    private PreparedConversationQuery prepareConversationQuery(
            Long appuserId,
            UUID conversationUuid,
            QueryRequest request,
            boolean validateArchIdConsistency
    ) {
        if (validateArchIdConsistency) {
            validateRequestArchIdMatchesRepoUuid(request);
        }
        AgentConversation conversation = conversationService.getOrCreateForQuery(
                appuserId,
                conversationUuid,
                request.getRepoUuid(),
                request.getQuestion()
        );

        String resolvedArchId = resolveArchIdForConversation(
                request.getArchId(),
                conversation.getRepoId(),
                validateArchIdConsistency
        );
        conversationService.saveMessage(conversation, AgentMessageRole.USER, request.getQuestion(), null);
        QueryIntent intent = routingService.classify(request.getQuestion());
        logAiQueryStarted("conversation_query_started", intent, resolvedArchId, request.getQuestion(), appuserId);

        ContextResult ctx = buildContext(intent, resolvedArchId, request.getQuestion());
        int tokenBudget = isLargeIntent(intent) ? maxTokensLarge : maxTokens;
        String wrappedContext = wrapAndTruncate(ctx.rawContext(), tokenBudget);
        boolean truncated = wrappedContext.endsWith("[TRUNCATED]");
        conversationService.saveMessage(conversation, AgentMessageRole.SYSTEM,
                buildSystemMessage(resolvedArchId, intent, truncated, wrappedContext), null);

        return new PreparedConversationQuery(conversation, resolvedArchId, intent, ctx, wrappedContext, truncated);
    }

    private String buildSystemMessage(String resolvedArchId, QueryIntent intent, boolean truncated, String wrappedContext) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("resolvedArchId", resolvedArchId);
            payload.put("intent", intent.name());
            payload.put("truncated", truncated);
            payload.put("context", wrappedContext);
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "resolvedArchId=" + resolvedArchId + ", intent=" + intent.name() + ", truncated=" + truncated;
        }
    }

    private ServerSentEvent<String> toTokenEvent(String token) {
        try {
            return ServerSentEvent.<String>builder()
                    .data(objectMapper.writeValueAsString(Map.of("type", "token", "content", token)))
                    .build();
        } catch (JsonProcessingException e) {
            return ServerSentEvent.<String>builder()
                    .data("{\"type\":\"token\",\"content\":\"\"}")
                    .build();
        }
    }

    private ServerSentEvent<String> toDoneEvent(QueryIntent intent, String topNode, List<String> keyNodes, boolean truncated) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "done");
            payload.put("intent", intent.name());
            payload.put("topNode", topNode);
            payload.put("keyNodes", keyNodes != null ? keyNodes : List.of());
            payload.put("truncated", truncated);
            return ServerSentEvent.<String>builder()
                    .data(objectMapper.writeValueAsString(payload))
                    .build();
        } catch (JsonProcessingException e) {
            return ServerSentEvent.<String>builder()
                    .data("{\"type\":\"done\"}")
                    .build();
        }
    }

    private ServerSentEvent<String> toErrorEvent(Throwable throwable) {
        return ServerSentEvent.<String>builder()
                .data("{\"type\":\"error\",\"message\":\"서버 오류가 발생했습니다\"}")
                .build();
    }

    private ServerSentEvent<String> toApiResultEvent(String eventName, Object data) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("status", SuccessCode.SUCCESS.name());
            payload.put("message", SuccessCode.SUCCESS.getMessage());
            payload.put("data", data);
            return ServerSentEvent.<String>builder()
                    .event(eventName)
                    .data(objectMapper.writeValueAsString(payload))
                    .build();
        } catch (JsonProcessingException e) {
            return ServerSentEvent.<String>builder()
                    .event(eventName)
                    .data("{\"status\":\"SUCCESS\",\"message\":\"요청이 성공했습니다.\",\"data\":null}")
                    .build();
        }
    }

    private ServerSentEvent<String> toApiResultErrorEvent() {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("status", ErrorCode.INTERNAL_SERVER_ERROR.name());
            payload.put("message", ErrorCode.INTERNAL_SERVER_ERROR.getMessage());
            payload.put("data", Map.of("type", "error"));
            return ServerSentEvent.<String>builder()
                    .event("error")
                    .data(objectMapper.writeValueAsString(payload))
                    .build();
        } catch (JsonProcessingException e) {
            return ServerSentEvent.<String>builder()
                    .event("error")
                    .data("{\"status\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 내부 오류가 발생했습니다.\",\"data\":null}")
                    .build();
        }
    }

    private void logAiQueryStarted(String event, QueryIntent intent, String resolvedArchId, String question, Long appuserId) {
        LogSanitizer.TextSummary questionSummary = LogSanitizer.summarizeText(question);
        log.info("ai.query.event event={} intent={} archId={} appuserId={} questionLength={} questionHash={}",
                event, intent, resolvedArchId, appuserId == null ? "-" : appuserId,
                questionSummary.length(), questionSummary.hash());
    }

    // ── archId 해석 ──────────────────────────────────────────────────────────

    private String resolveArchId(String archId) {
        if (archId != null && !archId.isBlank()) {
            return archId;
        }
        List<?> all = architectureRepository.findAll();
        if (all.size() == 1) {
            String resolved = ((Architecture) all.get(0)).getId();
            log.info("archId 생략 → 자동 채택: {}", resolved);
            return resolved;
        }
        if (all.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "{\"error\":{\"code\":\"ARCHID_REQUIRED\",\"message\":\"적재된 아키텍처가 없습니다.\"}}");
        }
        List<String> ids = all.stream()
                .map(a -> ((Architecture) a).getId())
                .toList();
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "{\"error\":{\"code\":\"ARCHID_REQUIRED\",\"availableArchIds\":" + ids + "}}");
    }

    private String resolveArchIdForConversation(String archId, Long repoId, boolean validateArchIdConsistency) {
        if (repoId == null) {
            return resolveArchId(archId);
        }
        GitHubRepositoryEntity repository = gitHubRepositoryRepository.findById(repoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "{\"error\":{\"code\":\"REPO_NOT_FOUND\",\"message\":\"저장소를 찾을 수 없습니다.\"}}"));
        if (validateArchIdConsistency) {
            validateArchIdMatchesRepository(archId, repository);
        }
        return toArchId(repository);
    }

    private void validateRequestArchIdMatchesRepoUuid(QueryRequest request) {
        if (request.getRepoUuid() == null || request.getArchId() == null || request.getArchId().isBlank()) {
            return;
        }
        gitHubRepositoryRepository.findByRepoUuid(request.getRepoUuid())
                .ifPresent(repository -> validateArchIdMatchesRepository(request.getArchId(), repository));
    }

    private void validateArchIdMatchesRepository(String archId, GitHubRepositoryEntity repository) {
        if (archId == null || archId.isBlank()) {
            return;
        }
        String repoArchId = toArchId(repository);
        String requestedArchId = archId.trim();
        if (!repoArchId.equalsIgnoreCase(requestedArchId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "{\"error\":{\"code\":\"ARCHID_REPO_MISMATCH\",\"message\":\"repoUuid와 archId가 서로 다른 저장소를 가리킵니다.\",\"repoArchId\":\""
                            + repoArchId + "\",\"requestedArchId\":\"" + requestedArchId + "\"}}");
        }
    }

    private String toArchId(GitHubRepositoryEntity repository) {
        return repository.getOwnerLogin() + "__" + repository.getRepoName();
    }

    private UUID findRepoUuid(Long repoId) {
        if (repoId == null) {
            return null;
        }
        return gitHubRepositoryRepository.findById(repoId)
                .map(GitHubRepositoryEntity::getRepoUuid)
                .orElse(null);
    }

    // ── Intent 라우팅 & 컨텍스트 조립 ────────────────────────────────────────

    private ContextResult buildContext(QueryIntent intent, String archId, String question) {
        return switch (intent) {
            case NODE_DESCRIBE, EDGE_DESCRIBE, NODE_LIST, GROUP_DESCRIBE, ARCH_COUNTS, GROUP_NODE_COUNT, NODE_FALLBACK -> {
                List<GraphResult> hits = graphRetriever.graphRetrieve(intent, archId, question);
                yield buildFromGraphHits(hits);
            }

            case HUB_NODES -> {
                List<GraphResult> hits = graphRetriever.graphRetrieve(intent, archId, question);
                yield buildHubFromGraphHits(hits);
            }

            case FALLBACK -> {
                List<GraphResult> combined = new ArrayList<>();
                combined.addAll(vectorRetriever.findSimilarEdges(archId, question));
                combined.addAll(vectorRetriever.findSimilarNodes(archId, question));
                yield buildFromGraphHits(combined);
            }
        };
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private ContextResult buildFromGraphHits(List<GraphResult> hits) {
        Map<String, Map<String, Object>> nodeMap = new LinkedHashMap<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        List<Map<String, Object>> groups = new ArrayList<>();
        List<Map<String, Object>> counts = new ArrayList<>();
        List<SourceRef> sources = new ArrayList<>();

        for (GraphResult hit : hits) {
            if (hit instanceof GraphResult.NodeResult node) {
                nodeMap.putIfAbsent(node.nodeId(), new LinkedHashMap<>(Map.of(
                        "nodeId",      node.nodeId(),
                        "type",        node.nodeType() != null ? node.nodeType() : "",
                        "label",       node.label(),
                        "description", truncateDescription(node.description()),
                        "path",        node.path() != null ? node.path() : ""
                )));
                sources.add(new SourceRef("node", node.nodeId(), node.label(),
                        truncateDescription(node.description())));
            } else if (hit instanceof GraphResult.EdgeResult edge) {
                Map<String, Object> edgeMap = new LinkedHashMap<>();
                edgeMap.put("edgeId",      edge.id());
                edgeMap.put("fromNodeId",  edge.fromNodeId());
                edgeMap.put("label",       edge.label());
                edgeMap.put("toNodeId",    edge.toNodeId());
                edgeMap.put("description", edge.description());
                edges.add(edgeMap);
                sources.add(new SourceRef("edge", edge.id(), edge.label(),
                        truncateDescription(edge.description())));
            } else if (hit instanceof GraphResult.GroupResult group) {
                Map<String, Object> groupMap = new LinkedHashMap<>();
                groupMap.put("id",          group.groupId());
                groupMap.put("label",       group.label());
                groupMap.put("description", truncateDescription(group.description()));
                groups.add(groupMap);
                sources.add(new SourceRef("group", group.groupId(), group.label(),
                        truncateDescription(group.description())));
            } else if (hit instanceof GraphResult.CountResult countResult) {
                Map<String, Object> countMap = new LinkedHashMap<>();
                countMap.put("type",  countResult.countType());
                countMap.put("value", countResult.count());
                if (countResult.refId() != null)  countMap.put("refId", countResult.refId());
                if (countResult.label() != null)  countMap.put("label", countResult.label());
                counts.add(countMap);
            }
        }

        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("nodes", new ArrayList<>(nodeMap.values()));
        graph.put("edges", edges);
        if (!groups.isEmpty()) {
            graph.put("groups", groups);
        }
        if (!counts.isEmpty()) {
            graph.put("counts", counts);
        }

        String rawContext;
        try {
            rawContext = objectMapper.writeValueAsString(Map.of("graph", graph));
        } catch (JsonProcessingException e) {
            log.warn("ai.context.event event=json_serialize_failed errorType={}", e.getClass().getName());
            rawContext = "";
        }
        return new ContextResult(rawContext, sources);
    }

    private ContextResult buildHubFromGraphHits(List<GraphResult> hits) {
        Map<String, Map<String, Object>> nodeMap = new LinkedHashMap<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        List<Map<String, Object>> groups = new ArrayList<>();
        List<SourceRef> sources = new ArrayList<>();

        for (GraphResult hit : hits) {
            if (hit instanceof GraphResult.NodeResult node) {
                nodeMap.putIfAbsent(node.nodeId(), new LinkedHashMap<>(Map.of(
                        "nodeId",      node.nodeId(),
                        "type",        node.nodeType() != null ? node.nodeType() : "",
                        "label",       node.label(),
                        "description", truncateDescription(node.description()),
                        "degree",      (int) node.degree(),
                        "path",        node.path() != null ? node.path() : "",
                        "groupId",     node.groupId() != null ? node.groupId() : "",
                        "hub",         node.hub()
                )));
                sources.add(new SourceRef("node", node.nodeId(), node.label(),
                        truncateDescription(node.description())));
            } else if (hit instanceof GraphResult.EdgeResult edge) {
                Map<String, Object> edgeMap = new LinkedHashMap<>();
                edgeMap.put("edgeId",      edge.id());
                edgeMap.put("fromNodeId",  edge.fromNodeId());
                edgeMap.put("label",       edge.label());
                edgeMap.put("toNodeId",    edge.toNodeId());
                edgeMap.put("description", edge.description());
                edges.add(edgeMap);
                sources.add(new SourceRef("edge", edge.id(), edge.label(),
                        truncateDescription(edge.description())));
            } else if (hit instanceof GraphResult.GroupResult group) {
                Map<String, Object> groupMap = new LinkedHashMap<>();
                groupMap.put("id",          group.groupId());
                groupMap.put("label",       group.label());
                groupMap.put("description", truncateDescription(group.description()));
                groups.add(groupMap);
                sources.add(new SourceRef("group", group.groupId(), group.label(),
                        truncateDescription(group.description())));
            }
        }

        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("nodes",  new ArrayList<>(nodeMap.values()));
        graph.put("edges",  edges);
        graph.put("groups", groups);

        String rawContext;
        try {
            rawContext = objectMapper.writeValueAsString(Map.of("graph", graph));
        } catch (JsonProcessingException e) {
            log.warn("ai.context.event event=json_serialize_failed errorType={}", e.getClass().getName());
            rawContext = "";
        }
        return new ContextResult(rawContext, sources);
    }

    private String wrapAndTruncate(String rawContext, int tokenLimit) {
        // 단순 문자 수 근사: 4문자 ≈ 1 토큰
        int charLimit = tokenLimit * 4;
        String body = rawContext;
        boolean truncated = false;
        if (body.length() > charLimit) {
            body = body.substring(0, charLimit);
            truncated = true;
        }
        // <context> 래핑은 시스템 프롬프트 템플릿({{context}})이 담당하므로 여기선 raw content만 반환
        return truncated ? body + "\n[TRUNCATED]" : body;
    }

    private String truncateDescription(String text) {
        if (text == null) return "";
        return text.length() > 2000 ? text.substring(0, 2000) : text;
    }

    private int estimateTokens(String text) {
        return text == null ? 0 : text.length() / 4;
    }

    private boolean isLargeIntent(QueryIntent intent) {
//        return intent == QueryIntent.HUB_NODES
//                || intent == QueryIntent.FLOW_TRACE;
        return false;
    }

    private record ContextResult(String rawContext, List<SourceRef> sources) {}

    private record PreparedConversationQuery(
            AgentConversation conversation,
            String resolvedArchId,
            QueryIntent intent,
            ContextResult context,
            String wrappedContext,
            boolean truncated
    ) {}

    private record NodeMetaResult(String answer, String topNode, List<String> keyNodes) {}

    @SuppressWarnings("unchecked")
    private NodeMetaResult extractNodeMeta(String raw) {
        Matcher m = NODE_META_PATTERN.matcher(raw);
        if (!m.find()) {
            return new NodeMetaResult(raw, null, List.of());
        }
        String cleanAnswer = raw.substring(0, m.start()).stripTrailing();
        try {
            Map<String, Object> map = objectMapper.readValue(m.group(1), Map.class);
            String topNode = (String) map.get("topNode");
            List<String> keyNodes = (List<String>) map.getOrDefault("keyNodes", List.of());
            return new NodeMetaResult(cleanAnswer, topNode, keyNodes);
        } catch (JsonProcessingException e) {
            LogSanitizer.TextSummary nodeMetaSummary = LogSanitizer.summarizeText(m.group(1));
            log.warn("ai.query.event event=node_meta_parse_failed nodeMetaLength={} nodeMetaHash={} errorType={}",
                    nodeMetaSummary.length(), nodeMetaSummary.hash(), e.getClass().getName());
            return new NodeMetaResult(cleanAnswer, null, List.of());
        }
    }

    // ── LLM-as-a-Judge ───────────────────────────────────────────────────────

    @Transactional
    public void evalAndSave(String question, String archId, String graphAnswer, String vectorAnswer) {
        try {
            String resolvedArchId = resolveArchId(archId);

            String rawGraph = ragJudgeAssistant.judge(question, extractJudgeTarget(graphAnswer));
            String rawVector = ragJudgeAssistant.judge(question, extractJudgeTarget(vectorAnswer));

            Integer graphScore = parseScore(rawGraph);
            Integer vectorScore = parseScore(rawVector);
            if (graphScore < 1) {
                logJudgeParseFailed("graph_answer_judge", rawGraph);
                graphScore = null;
            }
            if (vectorScore < 1) {
                logJudgeParseFailed("vector_answer_judge", rawVector);
                vectorScore = null;
            }
            qaEvalRepository.save(
                    QaEvalEntity.builder()
                            .archId(resolvedArchId)
                            .question(question)
                            .graphScore(graphScore)
                            .vectorScore(vectorScore)
                            .build()
            );
            log.info("QA eval 저장 완료: archId={}, graphScore={}, vectorScore={}", resolvedArchId, graphScore, vectorScore);
        } catch (Exception e) {
            log.warn("ai.eval.event event=qa_eval_save_failed errorType={}", e.getClass().getName());
        }
    }

    private String extractJudgeTarget(String answer) {
        if (answer == null) return "";
        String header = "### AI Agent 답변";
        int headerIdx = answer.indexOf(header);
        if (headerIdx < 0) return answer;
        String afterHeader = answer.substring(headerIdx + header.length());
        int sepIdx = afterHeader.indexOf("---");
        if (sepIdx < 0) return afterHeader.strip();
        String result = afterHeader.substring(0, sepIdx).strip();
        LogSanitizer.TextSummary targetSummary = LogSanitizer.summarizeText(result);
        log.info("ai.eval.event event=judge_target_extracted targetLength={} targetHash={}",
                targetSummary.length(), targetSummary.hash());
        return result;
    }

    @Transactional
    public void evalAndSaveRAG(String question, String archId) {
        try {
            String resolvedArchId = resolveArchId(archId);

            QueryIntent graphIntent = routingService.classify(question);
            String graphContext = buildContext(graphIntent, resolvedArchId, question).rawContext();
            String vectorContext = buildContext(QueryIntent.FALLBACK, resolvedArchId, question).rawContext();

            String rawGraph = ragJudgeAssistant.judgeContext(question, graphContext);
            String rawVector = ragJudgeAssistant.judgeContext(question, vectorContext);

            Integer graphScore = parseScore(rawGraph);
            Integer vectorScore = parseScore(rawVector);
            if (graphScore < 1) {
                logJudgeParseFailed("graph_answer_judge", rawGraph);
                graphScore = null;
            }
            if (vectorScore < 1) {
                logJudgeParseFailed("vector_answer_judge", rawVector);
                vectorScore = null;
            }
            qaEvalRepository.save(
                    QaEvalEntity.builder()
                            .archId(resolvedArchId)
                            .question("[RAG] " + question)
                            .graphScore(graphScore)
                            .vectorScore(vectorScore)
                            .build()
            );
            log.info("RAG eval 저장 완료: archId={}, graphScore={}, vectorScore={}", resolvedArchId, graphScore, vectorScore);
        } catch (Exception e) {
            log.warn("ai.eval.event event=rag_eval_save_failed errorType={}", e.getClass().getName());
        }
    }

    private void logJudgeParseFailed(String event, String rawJudgeResponse) {
        LogSanitizer.TextSummary rawSummary = LogSanitizer.summarizeText(rawJudgeResponse);
        log.warn("ai.eval.event event={} status=parse_failed rawLength={} rawHash={}",
                event, rawSummary.length(), rawSummary.hash());
    }

    private int parseScore(String raw) {
        if (raw == null || raw.isBlank()) return -1;
        Matcher matcher = SCORE_PATTERN.matcher(raw.strip());
        if (matcher.find()) {
            return Integer.parseInt(matcher.group());
        }
        return -1;
    }
}
