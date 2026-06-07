package com.ssafy.githubble.domain.ai.controller;

import com.ssafy.githubble.domain.ai.dto.request.ConversationTitleUpdateRequest;
import com.ssafy.githubble.domain.ai.dto.request.QueryRequest;
import com.ssafy.githubble.domain.ai.dto.response.ConversationDetailResponse;
import com.ssafy.githubble.domain.ai.dto.response.ConversationResponse;
import com.ssafy.githubble.domain.ai.dto.response.QueryResponse;
import com.ssafy.githubble.domain.ai.service.AgentConversationService;
import com.ssafy.githubble.domain.ai.service.QueryService;
import com.ssafy.githubble.global.code.SuccessCode;
import com.ssafy.githubble.global.dto.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.UUID;

@RestController
@RequestMapping("/v1/agent/conversations")
@RequiredArgsConstructor
@Tag(name = "AI Conversation", description = "AI 채팅방과 메시지 조회/관리 API")
@SecurityRequirement(name = "cookieAuth")
public class AgentConversationController {

    private final AgentConversationService conversationService;
    private final QueryService queryService;

    @Operation(summary = "AI 채팅방 목록 조회", description = "로그인 사용자의 특정 저장소 AI 채팅방 목록을 최신 수정 순으로 페이징 조회합니다. repoUuid는 필수입니다. 요청 body는 없습니다.")
    @GetMapping
    public ResponseEntity<ApiResult<Page<ConversationResponse>>> list(
            HttpServletRequest httpServletRequest,
            @Parameter(description = "조회할 저장소 UUID", required = true, example = "11111111-1111-1111-1111-111111111111")
            @RequestParam UUID repoUuid,
            @ParameterObject
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Long appuserId = (Long) httpServletRequest.getAttribute("appuserId");
        return ApiResult.success(SuccessCode.SUCCESS, conversationService.list(appuserId, repoUuid, pageable));
    }

    @Operation(summary = "AI 채팅방 상세 조회", description = "채팅방 정보와 저장된 메시지 목록을 조회합니다. path의 conversationUuid 필수.")
    @GetMapping("/{conversationUuid}")
    public ResponseEntity<ApiResult<ConversationDetailResponse>> detail(
            HttpServletRequest httpServletRequest,
            @PathVariable UUID conversationUuid
    ) {
        Long appuserId = (Long) httpServletRequest.getAttribute("appuserId");
        return ApiResult.success(SuccessCode.SUCCESS, conversationService.detail(appuserId, conversationUuid));
    }

    @Operation(summary = "AI 채팅방 제목 수정", description = "로그인 사용자가 소유한 AI 채팅방 제목을 수정합니다. path의 conversationUuid 필수. 요청 필드: title 필수, 최대 200자.")
    @PatchMapping("/{conversationUuid}/title")
    public ResponseEntity<ApiResult<ConversationResponse>> updateTitle(
            HttpServletRequest httpServletRequest,
            @PathVariable UUID conversationUuid,
            @Valid @RequestBody ConversationTitleUpdateRequest request
    ) {
        Long appuserId = (Long) httpServletRequest.getAttribute("appuserId");
        return ApiResult.success(SuccessCode.UPDATED,
                conversationService.updateTitle(appuserId, conversationUuid, request));
    }

    @Operation(summary = "AI 채팅방 삭제", description = "로그인 사용자가 소유한 AI 채팅방을 soft delete 처리합니다. path의 conversationUuid 필수. 요청 body는 없습니다.")
    @DeleteMapping("/{conversationUuid}")
    public ResponseEntity<ApiResult<Void>> delete(
            HttpServletRequest httpServletRequest,
            @PathVariable UUID conversationUuid
    ) {
        Long appuserId = (Long) httpServletRequest.getAttribute("appuserId");
        conversationService.delete(appuserId, conversationUuid);
        return ApiResult.success(SuccessCode.DELETED);
    }

    @Operation(summary = "새 AI 채팅 HTTP 질의", description = "새 채팅방을 생성하고 질문에 대한 AI 답변을 HTTP 응답으로 반환합니다. 실사용 흐름에서는 repoUuid 전송을 권장합니다. repoUuid가 있으면 채팅방이 해당 저장소에 연결되고, 서버가 저장소 기준 컨텍스트를 archId보다 우선합니다. archId는 diagram ingest/test 데이터 확인 등 GraphRAG 직접 질의용 보조값입니다. repoUuid 없이 archId만 보내면 답변과 채팅방 저장은 가능하지만 repo_id가 null일 수 있어 저장소별 채팅방 목록에는 포함되지 않을 수 있습니다. repoUuid와 archId를 함께 보내면 같은 저장소를 가리켜야 합니다.")
    @PostMapping("/query/http")
    public ResponseEntity<ApiResult<QueryResponse>> queryHttp(
            HttpServletRequest httpServletRequest,
            @Valid @RequestBody QueryRequest request
    ) {
        Long appuserId = getAppuserId(httpServletRequest);
        QueryResponse response = queryService.answerWithConversation(appuserId, null, request, false);
        return ApiResult.success(SuccessCode.SUCCESS, response);
    }

    @Operation(summary = "새 AI 채팅 HTTP 테스트 질의", description = "프론트엔드 개발용 테스트 API입니다. 새 채팅방을 생성하되 실제 LLM 토큰을 사용하지 않고 고정 테스트 답변을 HTTP 응답으로 반환합니다. 요청 body의 question은 형식 검증용이며 서버 테스트 값으로 덮어씁니다.")
    @PostMapping("/query/test")
    public ResponseEntity<ApiResult<QueryResponse>> queryTest(
            HttpServletRequest httpServletRequest,
            @Valid @RequestBody QueryRequest request
    ) {
        Long appuserId = getAppuserId(httpServletRequest);
        request.setQuestion("이 프로젝트의 github 관련된 부분 설명해주세요");
        request.setArchId("ahmedkhaleel2004__gitdiagram");
        QueryResponse response = queryService.answerWithConversation(appuserId, null, request, true);
        return ApiResult.success(SuccessCode.SUCCESS, response);
    }

    @Operation(summary = "새 AI 채팅 SSE 질의", description = "새 채팅방을 생성하고 질문에 대한 AI 답변을 SSE로 스트리밍합니다. 실사용 흐름에서는 repoUuid 전송을 권장합니다. repoUuid가 있으면 채팅방이 해당 저장소에 연결되고, 서버가 저장소 기준 컨텍스트를 archId보다 우선합니다. archId는 diagram ingest/test 데이터 확인 등 GraphRAG 직접 질의용 보조값입니다. repoUuid 없이 archId만 보내면 답변과 채팅방 저장은 가능하지만 repo_id가 null일 수 있어 저장소별 채팅방 목록에는 포함되지 않을 수 있습니다. repoUuid와 archId를 함께 보내면 같은 저장소를 가리켜야 합니다.")
    @PostMapping(value = "/query", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> query(
            HttpServletRequest httpServletRequest,
            @Valid @RequestBody QueryRequest request
    ) {
        Long appuserId = getAppuserId(httpServletRequest);
        return queryService.answerFluxWithConversation(appuserId, null, request);
    }

    @Operation(summary = "기존 AI 채팅방 HTTP 질의", description = "path의 conversationUuid 필수. 해당 채팅방에서 이어서 질문하고 HTTP 응답으로 반환합니다. 기존 채팅방에 연결된 저장소가 있으면 요청 body의 archId보다 저장소 기준 컨텍스트를 우선합니다. archId를 함께 보내면 채팅방의 저장소와 같은 owner__repo 값이어야 합니다. 기존 채팅방 질의에서는 body의 repoUuid를 사용하지 않습니다.")
    @PostMapping("/{conversationUuid}/query/http")
    public ResponseEntity<ApiResult<QueryResponse>> queryHttpInConversation(
            HttpServletRequest httpServletRequest,
            @PathVariable UUID conversationUuid,
            @Valid @RequestBody QueryRequest request
    ) {
        Long appuserId = getAppuserId(httpServletRequest);
        QueryResponse response = queryService.answerWithConversation(appuserId, conversationUuid, request, false);
        return ApiResult.success(SuccessCode.SUCCESS, response);
    }

    @Operation(summary = "기존 AI 채팅방 SSE 질의", description = "path의 conversationUuid 필수. 해당 채팅방에서 이어서 질문하고 SSE로 스트리밍합니다. 기존 채팅방에 연결된 저장소가 있으면 요청 body의 archId보다 저장소 기준 컨텍스트를 우선합니다. archId를 함께 보내면 채팅방의 저장소와 같은 owner__repo 값이어야 합니다. 기존 채팅방 질의에서는 body의 repoUuid를 사용하지 않습니다.")
    @PostMapping(value = "/{conversationUuid}/query", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> queryInConversation(
            HttpServletRequest httpServletRequest,
            @PathVariable UUID conversationUuid,
            @Valid @RequestBody QueryRequest request
    ) {
        Long appuserId = getAppuserId(httpServletRequest);
        return queryService.answerFluxWithConversation(appuserId, conversationUuid, request);
    }

    private Long getAppuserId(HttpServletRequest httpServletRequest) {
        return (Long) httpServletRequest.getAttribute("appuserId");
    }
}
