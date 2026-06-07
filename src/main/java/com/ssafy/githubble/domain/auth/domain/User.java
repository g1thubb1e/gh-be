package com.ssafy.githubble.domain.auth.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Builder
@Table(name = "app_user")
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class User {
    @Id
    @Column(name = "app_user_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long appUserId;

    @Column(name = "app_user_uuid", nullable = false, unique = true)
    UUID appUserUuid;

    @Column(name = "github_id", nullable = false, unique = true)
    Long githubId;

    @Column
    String username;

    @Column
    String email;

    @Column(name = "github_access_token")
    String githubAccessToken;

    @Column(name = "avatar_url")
    String avatarUrl;

    @Column(name = "is_deleted", nullable = false)
    Boolean isDeleted;

    @CreatedDate
    @Column(updatable = false)
    LocalDateTime createdAt;

    @LastModifiedDate
    @Column
    LocalDateTime updatedAt;

    @PrePersist
    public void generateId() {
        if (this.isDeleted == null) {
            this.isDeleted = false;
        }
        if (this.appUserUuid == null) {
            this.appUserUuid = UUID.randomUUID();
        }
    }

    public void updateGithubProfile(String username, String email, String githubAccessToken, String avatarUrl) {
        this.username = username;
        this.email = email;
        this.githubAccessToken = githubAccessToken;
        this.avatarUrl = avatarUrl;
    }

    public void softDelete(){
        this.isDeleted = true;
    }
}
