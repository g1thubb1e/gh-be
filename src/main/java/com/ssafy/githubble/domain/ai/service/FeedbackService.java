package com.ssafy.githubble.domain.ai.service;

import com.ssafy.githubble.domain.ai.dto.response.CompareFeedbackResponse;
import com.ssafy.githubble.domain.ai.dto.response.PagedResponse;
import com.ssafy.githubble.domain.ai.dto.response.QaEvalResponse;
import com.ssafy.githubble.domain.ai.dto.response.RatingFeedbackResponse;
import com.ssafy.githubble.domain.ai.entity.CompareFeedbackEntity;
import com.ssafy.githubble.domain.ai.entity.RatingFeedbackEntity;
import com.ssafy.githubble.domain.ai.repository.CompareFeedbackRepository;
import com.ssafy.githubble.domain.ai.repository.QaEvalRepository;
import com.ssafy.githubble.domain.ai.repository.RatingFeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final CompareFeedbackRepository compareFeedbackRepository;
    private final RatingFeedbackRepository ratingFeedbackRepository;
    private final QaEvalRepository qaEvalRepository;

    @Transactional
    public void saveCompareFeedback(String question, String archId, String selectedAnswer,
                                    String answerA, String answerB, String questioner, Double responseTime) {
        compareFeedbackRepository.save(
                CompareFeedbackEntity.builder()
                        .question(question)
                        .archId(archId)
                        .selectedAnswer(selectedAnswer)
                        .answerA(answerA)
                        .answerB(answerB)
                        .questioner(questioner)
                        .responseTime(responseTime)
                        .build()
        );
    }

    @Transactional
    public void saveRatingFeedback(String question, String archId, Integer score,
                                   String answer, String comment, String type,
                                   String questioner, Double responseTime) {
        ratingFeedbackRepository.save(
                RatingFeedbackEntity.builder()
                        .question(question)
                        .archId(archId)
                        .score(score)
                        .answer(answer)
                        .comment(comment)
                        .type(type)
                        .questioner(questioner)
                        .responseTime(responseTime)
                        .build()
        );
    }

    @Transactional(readOnly = true)
    public PagedResponse<CompareFeedbackResponse> listCompareFeedbacks(int page, int size) {
        var result = compareFeedbackRepository.findAll(
                PageRequest.of(page, size, Sort.by("createdAt").descending())
        );
        List<CompareFeedbackResponse> content = result.getContent().stream()
                .map(e -> new CompareFeedbackResponse(
                        e.getId(), e.getQuestion(), e.getArchId(), e.getSelectedAnswer(),
                        e.getAnswerA(), e.getAnswerB(), e.getQuestioner(),
                        e.getResponseTime(), e.getCreatedAt()))
                .toList();
        return new PagedResponse<>(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PagedResponse<RatingFeedbackResponse> listRatingFeedbacks(int page, int size) {
        var result = ratingFeedbackRepository.findAll(
                PageRequest.of(page, size, Sort.by("createdAt").descending())
        );
        List<RatingFeedbackResponse> content = result.getContent().stream()
                .map(e -> new RatingFeedbackResponse(
                        e.getId(), e.getQuestion(), e.getArchId(), e.getScore(),
                        e.getAnswer(), e.getComment(), e.getType(),
                        e.getQuestioner(), e.getResponseTime(), e.getCreatedAt()))
                .toList();
        return new PagedResponse<>(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PagedResponse<QaEvalResponse> listQaEvals(int page, int size) {
        var result = qaEvalRepository.findAll(
                PageRequest.of(page, size, Sort.by("createdAt").descending())
        );
        List<QaEvalResponse> content = result.getContent().stream()
                .map(e -> new QaEvalResponse(
                        e.getId(), e.getArchId(), e.getQuestion(),
                        e.getGraphScore(), e.getVectorScore(), e.getCreatedAt()))
                .toList();
        return new PagedResponse<>(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }
}
