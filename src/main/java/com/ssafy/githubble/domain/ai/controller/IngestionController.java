package com.ssafy.githubble.domain.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.githubble.domain.ai.dto.request.IngestRequest;
import com.ssafy.githubble.domain.ai.service.IngestionService;
import com.ssafy.githubble.domain.github.domain.enums.GenerationJobEvent;
import com.ssafy.githubble.domain.github.domain.enums.GenerationJobStep;
import com.ssafy.githubble.global.etc.JobManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/v1/ingest")
@RequiredArgsConstructor
@Tag(name = "AI Ingestion", description = "아키텍처 그래프 데이터 적재 API")
public class IngestionController {

    private final IngestionService ingestionService;
    private final JobManager jobManager;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    @Operation(summary = "아키텍처 데이터 적재", description = "요청 본문의 아키텍처 데이터를 Neo4j와 embedding 저장소에 적재합니다. 요청 필드: username 필수, repo 필수, graph 필수, attempt 선택.")
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> ingest(@Valid @RequestBody IngestRequest request) {
        String jobId = request.username() + "/" + request.repo();
        jobManager.send(jobId, GenerationJobEvent.STEP_STARTED,"RAG 생성 작업 시작");
        Map<String, Object> result =  ingestionService.ingest(request);
        jobManager.jobCompleted(
                jobId, GenerationJobEvent.STEP_COMPLETED, GenerationJobStep.RAG);
        if(jobManager.completeCheck(jobId)){
            jobManager.send(jobId, GenerationJobEvent.JOB_COMPLETED, "전체 작업 완료");
            jobManager.complete(jobId);
        }
        return result;
    }

    @Operation(summary = "아키텍처 테스트 데이터 적재", description = "docs/test-data/test_diagram.json 파일을 읽어 테스트용 아키텍처 데이터를 적재합니다. 요청 body는 없습니다.")
    @PostMapping("/test")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> ingest() throws IOException {
        // 1. 프로젝트 루트의 test-data 폴더 내 파일을 Resource로 읽어옴
        // "file:" 접두사를 사용하면 프로젝트 루트 경로를 기준으로 탐색합니다.
        Resource resource = resourceLoader.getResource("file:docs/test-data/test_diagram.json");

        // 2. JSON 파일을 IngestRequest 객체로 역직렬화
        IngestRequest request = objectMapper.readValue(resource.getInputStream(), IngestRequest.class);

        // 3. 서비스 레이어 호출
        return ingestionService.ingest(request);
    }
}
