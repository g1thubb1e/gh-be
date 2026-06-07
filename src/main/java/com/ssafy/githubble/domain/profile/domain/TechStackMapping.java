package com.ssafy.githubble.domain.profile.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tech_stack_mapping")
@Getter
@NoArgsConstructor
public class TechStackMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "techstack_mapping_id")
    private Long techstackMappingId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gitprofile_id", nullable = false)
    private GithubProfile profile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "techstack_id", nullable = false)
    private TechStack techStack;

    public TechStackMapping(GithubProfile profile, TechStack techStack) {
        this.profile = profile;
        this.techStack = techStack;
    }
}
