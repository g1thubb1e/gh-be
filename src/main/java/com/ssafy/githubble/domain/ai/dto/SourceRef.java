package com.ssafy.githubble.domain.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SourceRef {
    private final String type;    // "group" | "node" | "edge"
    private final String id;
    private final String label;
    private final String snippet;
}
