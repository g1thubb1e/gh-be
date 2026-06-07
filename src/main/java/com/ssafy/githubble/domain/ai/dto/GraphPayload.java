package com.ssafy.githubble.domain.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class GraphPayload {

    private List<GroupDto> groups = new ArrayList<>();

    private List<NodeDto> nodes = new ArrayList<>();

    private List<EdgeDto> edges = new ArrayList<>();

    private String rawText;

    @Getter
    @Setter
    public static class GroupDto {
        private String id;
        private String label;
        private String description;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NodeDto {
        private String id;
        private String groupId;
        private String label;
        private String type;
        private String description;
        private String path;
        private String shape;
        private List<String> representativeFiles;
    }

    @Getter
    @Setter
    public static class EdgeDto {
        private String id;
        private String from;
        private String to;
        private String label;
        private String description;
        private String style;
    }
}
