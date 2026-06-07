package com.ssafy.githubble.domain.ai.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "query_qa_eval")
@Getter
@NoArgsConstructor
public class QaEvalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "arch_id", nullable = false, length = 255)
    private String archId;

    @Column(columnDefinition = "text", nullable = false)
    private String question;

    @Column(name = "graph_score")
    private Integer graphScore;

    @Column(name = "vector_score")
    private Integer vectorScore;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public QaEvalEntity(String archId, String question, Integer graphScore, Integer vectorScore) {
        this.archId = archId;
        this.question = question;
        this.graphScore = graphScore;
        this.vectorScore = vectorScore;
    }
}