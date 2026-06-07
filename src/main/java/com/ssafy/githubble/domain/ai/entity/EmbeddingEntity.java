package com.ssafy.githubble.domain.ai.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "graphrag_embeddings")
// @Index 는 생성하지 않는다. 인덱스 정답 소스는 src/main/resources/schema.sql.
@Getter
@Setter
@NoArgsConstructor
public class EmbeddingEntity implements Persistable<UUID> {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "arch_id", nullable = false, length = 255)
    private String archId;

    @Column(nullable = false, length = 32)
    private String kind;

    @Column(name = "ref_id", nullable = false, length = 512)
    private String refId;

    @Column(length = 512)
    private String label;

    @Column(columnDefinition = "text", nullable = false)
    private String content;

    // node 전용
    @Column(name = "node_id", length = 512)
    private String nodeId;

    @Column(name = "node_type", length = 128)
    private String nodeType;

    @Column(columnDefinition = "text")
    private String description;

    @Column(columnDefinition = "text")
    private String path;

    // edge 전용
    @Column(name = "from_node_id", length = 512)
    private String fromNodeId;

    @Column(name = "to_node_id", length = 512)
    private String toNodeId;

    // Plan B: PgVectorUserType maps float[] <-> vector(3072) via PGobject.
    @Type(PgVectorUserType.class)
    @Column(columnDefinition = "vector(3072)", nullable = false)
    private float[] embedding;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Transient
    private boolean newEntity = true;

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.newEntity = false;
    }
}
