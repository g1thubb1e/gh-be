package com.ssafy.githubble.domain.ai.entity;

import com.ssafy.githubble.domain.auth.domain.User;
import com.ssafy.githubble.domain.github.domain.GitHubRepositoryEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Table(name = "agent_conversation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class AgentConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "conversation_id")
    private Long conversationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_user_id", nullable = false)
    private User appUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repo_id")
    private GitHubRepositoryEntity repository;

    @Column(name = "title", length = 200)
    private String title;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "conversation_uuid", nullable = false, unique = true)
    private UUID conversationUuid;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted;

    public static AgentConversation create(User appUser, GitHubRepositoryEntity repository, String title) {
        AgentConversation conversation = new AgentConversation();
        conversation.appUser = appUser;
        conversation.repository = repository;
        conversation.title = normalizeTitle(title);
        return conversation;
    }

    public Long getAppuserId() {
        return appUser == null ? null : appUser.getAppUserId();
    }

    public Long getRepoId() {
        return repository == null ? null : repository.getRepoId();
    }

    @PrePersist
    public void prePersist() {
        if (conversationUuid == null) {
            conversationUuid = UUID.randomUUID();
        }
        if (isDeleted == null) {
            isDeleted = false;
        }
        title = normalizeTitle(title);
    }

    public void updateTitle(String title) {
        this.title = normalizeTitle(title);
    }

    public void markDeleted() {
        this.isDeleted = true;
    }

    private static String normalizeTitle(String title) {
        String fallback = "새 채팅";
        if (title == null || title.isBlank()) {
            return fallback;
        }
        String normalized = title.trim().replaceAll("\\s+", " ");
        return normalized.length() > 200 ? normalized.substring(0, 200) : normalized;
    }
}
