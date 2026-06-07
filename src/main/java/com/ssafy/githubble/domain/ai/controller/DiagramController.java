package com.ssafy.githubble.domain.ai.controller;

import com.ssafy.githubble.domain.ai.dto.DiagramDetail;
import com.ssafy.githubble.domain.ai.dto.DiagramSummary;
import com.ssafy.githubble.domain.ai.dto.response.PagedResponse;
import com.ssafy.githubble.domain.ai.service.IngestionService;
import com.ssafy.githubble.neo4j.entity.Architecture;
import com.ssafy.githubble.neo4j.repository.ArchitectureRepository;
import com.ssafy.githubble.neo4j.repository.GraphQueryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/v1/diagrams")
@RequiredArgsConstructor
@Tag(name = "AI Diagram", description = "적재된 아키텍처 다이어그램 조회/삭제 API")
public class DiagramController {

    private final GraphQueryRepository graphQueryRepository;
    private final ArchitectureRepository architectureRepository;
    private final IngestionService ingestionService;

    @Operation(summary = "아키텍처 다이어그램 목록 조회", description = "적재된 아키텍처 다이어그램 목록을 페이지 단위로 조회합니다.")
    @GetMapping
    public PagedResponse<DiagramSummary> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (size > 100) size = 100;
        long skip = (long) page * size;
        long totalElements = graphQueryRepository.countAll();
        long totalPages = totalElements == 0 ? 0 : (totalElements + size - 1) / size;

        List<DiagramSummary> content = graphQueryRepository.findAllPaged(skip, size).stream()
                .map(a -> new DiagramSummary(
                        a.getId(),
                        a.getUsername(),
                        a.getRepo(),
                        a.getLastAttempt(),
                        a.getIngestedAt()))
                .toList();

        return new PagedResponse<>(content, page, size, totalElements, totalPages);
    }

    @Operation(summary = "아키텍처 다이어그램 상세 조회", description = "아키텍처 ID로 다이어그램 상세 정보와 group/node/edge 개수를 조회합니다.")
    @GetMapping("/{archId}")
    public DiagramDetail detail(@PathVariable String archId) {
        Architecture architecture = architectureRepository.findById(archId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "아키텍처를 찾을 수 없습니다: " + archId));

        long groupCount = graphQueryRepository.countGroups(archId);
        long nodeCount = graphQueryRepository.countNodes(archId);
        long edgeCount = graphQueryRepository.countEdges(archId);

        return new DiagramDetail(architecture, groupCount, nodeCount, edgeCount);
    }

    @Operation(summary = "아키텍처 다이어그램 삭제", description = "아키텍처 ID에 해당하는 다이어그램과 관련 데이터를 삭제합니다.")
    @DeleteMapping("/{archId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String archId) {
        if (!architectureRepository.existsById(archId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "아키텍처를 찾을 수 없습니다: " + archId);
        }
        ingestionService.delete(archId);
    }
}
