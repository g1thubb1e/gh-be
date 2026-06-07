package com.ssafy.githubble.domain.ai.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "query_rating_feedback")
@Getter
@NoArgsConstructor
public class RatingFeedbackEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(columnDefinition = "text", nullable = false)
    private String question;

    @Column(name = "arch_id", nullable = false, length = 255)
    private String archId;

    // 1~10
    @Column(nullable = false)
    private Integer score;

    @Column(columnDefinition = "text", nullable = false)
    private String answer;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(name = "type", nullable = false, length = 32)
    private String type;

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
    public RatingFeedbackEntity(String question, String archId, Integer score,
                                String answer, String comment, String type,
                                String questioner, Double responseTime) {
        this.question = question;
        this.archId = archId;
        this.score = score;
        this.answer = answer;
        this.comment = comment;
        this.type = type;
        this.questioner = questioner;
        this.responseTime = responseTime;
    }
}
