package com.ssafy.githubble.domain.ai.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "query_compare_feedback")
@Getter
@NoArgsConstructor
public class CompareFeedbackEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(columnDefinition = "text", nullable = false)
    private String question;

    @Column(name = "arch_id", nullable = false, length = 255)
    private String archId;

    // 'A' 또는 'B'
    @Column(name = "selected_answer", nullable = false, length = 1)
    private String selectedAnswer;

    @Column(name = "answer_a", columnDefinition = "text", nullable = false)
    private String answerA;

    @Column(name = "answer_b", columnDefinition = "text", nullable = false)
    private String answerB;

    @Column(name = "questioner", nullable = false, length = 255)
    private String questioner;

    @Column(name = "response_time")
    private Double responseTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public CompareFeedbackEntity(String question, String archId, String selectedAnswer,
                                 String answerA, String answerB, String questioner, Double responseTime) {
        this.question = question;
        this.archId = archId;
        this.selectedAnswer = selectedAnswer;
        this.answerA = answerA;
        this.answerB = answerB;
        this.questioner = questioner;
        this.responseTime = responseTime;
    }
}
