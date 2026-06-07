package com.ssafy.githubble.domain.ai.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Table(name = "agent_message")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class AgentMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long messageId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private AgentConversation conversation;

    @Column(name = "app_user_id", nullable = false)
    private Long appuserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private AgentMessageRole role;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "message_uuid", nullable = false, unique = true)
    private UUID messageUuid;

    public static AgentMessage create(AgentConversation conversation, AgentMessageRole role, String content, String errorMessage) {
        AgentMessage message = new AgentMessage();
        message.conversation = conversation;
        message.appuserId = conversation.getAppuserId();
        message.role = role;
        message.content = content;
        message.errorMessage = errorMessage;
        message.messageUuid = UUID.randomUUID();
        return message;
    }

    @PrePersist
    public void prePersist() {
        if (messageUuid == null) {
            messageUuid = UUID.randomUUID();
        }
    }
}
