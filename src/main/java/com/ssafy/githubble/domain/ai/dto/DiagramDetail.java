package com.ssafy.githubble.domain.ai.dto;

import com.ssafy.githubble.neo4j.entity.Architecture;
import lombok.Getter;

@Getter
public class DiagramDetail {
    private final String archId;
    private final String username;
    private final String repo;
    private final String lastAttempt;
    private final Long ingestedAt;
    private final long groupCount;
    private final long nodeCount;
    private final long edgeCount;

    public DiagramDetail(Architecture architecture, long groupCount, long nodeCount, long edgeCount) {
        this.archId = architecture.getId();
        this.username = architecture.getUsername();
        this.repo = architecture.getRepo();
        this.lastAttempt = architecture.getLastAttempt();
        this.ingestedAt = architecture.getIngestedAt();
        this.groupCount = groupCount;
        this.nodeCount = nodeCount;
        this.edgeCount = edgeCount;
    }
}
