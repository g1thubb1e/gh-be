package com.ssafy.githubble.domain.profile.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tech_stack")
@Getter
@NoArgsConstructor
public class TechStack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "techstack_id")
    private Long techstackId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "techstack_uuid", nullable = false, unique = true, updatable = false)
    private UUID techstackUuid;

    @Column(name = "icon", length = 255)
    private String icon; // S3 key (e.g. tech-stack-icons/nodejs.svg)

    @Column(name = "color", length = 255)
    private String color; // hex color (e.g. #6DB33F)

    @PrePersist
    public void prePersist() {
        if (this.techstackUuid == null) {
            this.techstackUuid = UUID.randomUUID();
        }
    }

    public TechStack(String name, String icon, String color) {
        this.name = name;
        this.icon = icon;
        this.color = color;
    }
}
