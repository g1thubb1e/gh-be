package com.ssafy.githubble.domain.ai.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record CompareFeedbackResponse(
        UUID id,
        String question,
        String archId,
        String selectedAnswer,
        String answerA,
        String answerB,
        String questioner,
        Double responseTime,
        LocalDateTime createdAt
) {}