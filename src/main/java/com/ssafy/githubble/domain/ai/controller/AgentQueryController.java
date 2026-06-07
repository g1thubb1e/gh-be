package com.ssafy.githubble.domain.ai.controller;

import com.ssafy.githubble.domain.ai.component.QueryIntent;
import com.ssafy.githubble.domain.ai.dto.request.QueryRequest;
import com.ssafy.githubble.domain.ai.dto.response.QueryResponse;
import com.ssafy.githubble.domain.ai.service.QueryService;
import com.ssafy.githubble.domain.ai.service.RoutingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/v1/agent")
@RequiredArgsConstructor
@Tag(name = "AI Query", description = "AI 질의응답 및 검색 컨텍스트 확인 API")
public class AgentQueryController {

    private final QueryService queryService;
    private final RoutingService routingService;

    @Operation(
            summary = "AI 답변 A/B 비교 생성",
            description = "질문과 archId를 기준으로 Graph RAG 답변과 Vector RAG 답변을 함께 생성하고 라우팅 결과를 반환합니다. QA 평가용 공개 API이며 인증과 대화방 저장을 수행하지 않습니다."
    )
    @PostMapping("/qa-answer")
    public QaResponse queryQaAnswer(@Valid @RequestBody QueryRequest request) {
        QueryIntent route = routingService.classify(request.getQuestion());
        QueryResponse answerA = queryService.answer(request.getQuestion(), request.getArchId(), false);
        QueryResponse answerB = queryService.answerVector(request.getQuestion(), request.getArchId());

        String answerAText = answerA.getAnswer();
        String answerBText = answerB.getAnswer();
//        CompletableFuture.runAsync(() ->
//                queryService.evalAndSave(request.getQuestion(), request.archId(), answerAText, answerBText));
        CompletableFuture.runAsync(() ->
                queryService.evalAndSaveRAG(request.getQuestion(), request.getArchId()));

        return new QaResponse(answerA, answerB, route);
    }

    @Operation(
            summary = "AI 질문 라우팅 확인",
            description = "질문을 분석해 AI 질의 의도를 분류한 QueryIntent를 반환합니다. 공개 API이며 인증, 답변 생성, 대화방 저장은 수행하지 않습니다."
    )
    @PostMapping("/route")
    public QueryIntent routeTest(@Valid @RequestBody QueryRequest request) {
        return queryService.route(request.getQuestion());
    }

    record QaResponse(QueryResponse answerA, QueryResponse answerB, QueryIntent route) {}
}
