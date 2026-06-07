package com.ssafy.githubble.domain.ai.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ssafy.githubble.domain.ai.dto.GraphPayload;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IngestRequest(
        @NotBlank
        @Schema(description = "필수. 아키텍처 데이터의 GitHub owner 또는 organization 이름입니다.", example = "spring-projects", requiredMode = Schema.RequiredMode.REQUIRED)
        String username,

        @NotBlank
        @Schema(description = "필수. 아키텍처 데이터의 저장소 이름입니다.", example = "spring-petclinic", requiredMode = Schema.RequiredMode.REQUIRED)
        String repo,

        @Schema(description = "선택. 같은 저장소에 대한 적재 시도를 구분하는 값입니다. 생략 시 null로 저장될 수 있습니다.", example = "main-20260504-101530", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String attempt,

        @NotNull
        @Valid
        @Schema(description = "필수. Neo4j와 embedding 저장소에 적재할 아키텍처 그래프 페이로드입니다.", requiredMode = Schema.RequiredMode.REQUIRED)
        GraphPayload graph,

        @Schema(description = "선택. 대표 파일 내용 목록입니다. 각 항목은 path(파일 경로), content(파일 내용), label(노드 레이블)을 포함합니다.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<FileContentEntry> fileContents
) {
    public record FileContentEntry(
            String path,
            String content,
            String label
    ) {}
}
