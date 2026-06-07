package com.ssafy.githubble.domain.ai.dto.request;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class QueryRequest {

    @NotBlank
    @Schema(description = "필수. AI에게 전달할 사용자 질문입니다.", example = "이 저장소의 인증 흐름은 어떤 파일에서 시작하나요?", requiredMode = Schema.RequiredMode.REQUIRED)
    private String question;

    @Schema(description = "선택. GraphRAG 아키텍처 ID입니다. 실사용 저장형 채팅에서는 repoUuid 사용을 권장하며, archId는 diagram ingest/test 데이터 확인 등 직접 질의용 보조값입니다. repoUuid 없이 archId만 보내면 채팅방은 저장되지만 저장소 repo_id가 null일 수 있습니다. repoUuid와 archId를 함께 보내는 경우 둘은 같은 저장소를 가리켜야 합니다.", example = "ahmedkhaleel2004__gitdiagram", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String archId; // null/blank 허용 — 생략 시 resolveArchId() 자동 채택

    @Schema(description = "선택(nullable). 새 채팅방을 연결할 저장소 UUID입니다. 실사용 저장형 채팅에서는 이 값을 보내는 것을 권장합니다. repoUuid가 있으면 채팅방의 repo_id가 저장소에 연결되고, 서버가 archId보다 저장소 기준 컨텍스트를 우선합니다. archId도 함께 보내면 repoUuid가 가리키는 저장소의 owner__repo 값과 일치해야 합니다.", example = "11111111-1111-1111-1111-111111111111", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private UUID repoUuid;
}
