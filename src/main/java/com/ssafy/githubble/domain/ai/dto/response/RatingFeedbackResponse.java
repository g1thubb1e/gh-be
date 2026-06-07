package com.ssafy.githubble.domain.ai.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record RatingFeedbackResponse(
        UUID id,
        String question,
        String archId,
        Integer score,
        String answer,
        String comment,
        String type,
        String questioner,
        Double responseTime,
        LocalDateTime createdAt
) {}