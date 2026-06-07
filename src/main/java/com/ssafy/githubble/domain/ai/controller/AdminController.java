package com.ssafy.githubble.domain.ai.controller;

import com.ssafy.githubble.domain.ai.dto.response.CompareFeedbackResponse;
import com.ssafy.githubble.domain.ai.dto.response.PagedResponse;
import com.ssafy.githubble.domain.ai.dto.response.QaEvalResponse;
import com.ssafy.githubble.domain.ai.dto.response.RatingFeedbackResponse;
import com.ssafy.githubble.domain.ai.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final FeedbackService feedbackService;

    @GetMapping("/qa-eval")
    public ResponseEntity<PagedResponse<QaEvalResponse>> listQaEvals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(feedbackService.listQaEvals(page, size));
    }

    @GetMapping("/compare-feedback")
    public ResponseEntity<PagedResponse<CompareFeedbackResponse>> listCompareFeedbacks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size) {
        return ResponseEntity.ok(feedbackService.listCompareFeedbacks(page, size));
    }

    @GetMapping("/rating-feedback")
    public ResponseEntity<PagedResponse<RatingFeedbackResponse>> listRatingFeedbacks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(feedbackService.listRatingFeedbacks(page, size));
    }
}