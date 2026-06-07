package com.ssafy.githubble.domain.ai.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RatingFeedbackRequest(
        @NotBlank String question,
        @NotBlank String archId,
        @NotNull @Min(1) @Max(10) Integer score,
        @NotBlank String answer,
        String comment,
        @NotBlank String type,
        @NotBlank String questioner,
        Double responseTime
) {}
