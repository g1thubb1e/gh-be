package com.ssafy.githubble.domain.ai.dto.response;

import com.ssafy.githubble.domain.ai.component.QueryIntent;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class QueryResponse {
    private final String answer;
    private final QueryIntent intent;
    private final String topNode;
    private final List<String> keyNodes;
    private final boolean truncated;
    private final String resolvedArchId; // archId 생략 시 자동 채택된 값 (정보 제공용)
    private final UUID conversationUuid;
    private final UUID repoUuid;
}
