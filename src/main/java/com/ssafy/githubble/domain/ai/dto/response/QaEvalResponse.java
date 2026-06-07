package com.ssafy.githubble.domain.ai.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record QaEvalResponse(
        UUID id,
        String archId,
        String question,
        Integer graphScore,
        Integer vectorScore,
        LocalDateTime createdAt
) {}