package com.ssafy.githubble.domain.ai.controller;

import com.ssafy.githubble.domain.ai.dto.request.CompareFeedbackRequest;
import com.ssafy.githubble.domain.ai.dto.request.RatingFeedbackRequest;
import com.ssafy.githubble.domain.ai.service.FeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping("/compare")
    public ResponseEntity<Void> compareAnswers(@Valid @RequestBody CompareFeedbackRequest request) {
        feedbackService.saveCompareFeedback(
                request.question(),
                request.archId(),
                request.selectedAnswer(),
                request.answerA(),
                request.answerB(),
                request.questioner(),
                request.responseTime()
        );
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/rate")
    public ResponseEntity<Void> rateAnswer(@Valid @RequestBody RatingFeedbackRequest request) {
        feedbackService.saveRatingFeedback(
                request.question(),
                request.archId(),
                request.score(),
                request.answer(),
                request.comment(),
                request.type(),
                request.questioner(),
                request.responseTime()
        );
        return ResponseEntity.noContent().build();
    }
}
