package com.ssafy.githubble.domain.ai.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CompareFeedbackRequest(
        @NotBlank String question,
        @NotBlank String archId,
        @NotBlank @Pattern(regexp = "^[AB]$", message = "selectedAnswer must be 'A' or 'B'") String selectedAnswer,
        @NotBlank String answerA,
        @NotBlank String answerB,
        @NotBlank String questioner,
        Double responseTime
) {}
