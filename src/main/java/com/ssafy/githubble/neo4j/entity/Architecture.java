package com.ssafy.githubble.neo4j.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.ArrayList;
import java.util.List;

@Node("Architecture")
@Getter
@Setter
public class Architecture {

    @Id
    private String id; // username__repo

    private String username;
    private String repo;
    private String lastAttempt;
    private String rawText;
    private Long ingestedAt;

    @Relationship(type = "CONTAINS_GROUP", direction = Relationship.Direction.OUTGOING)
    private List<Group> groups = new ArrayList<>();

    @Relationship(type = "CONTAINS_NODE", direction = Relationship.Direction.OUTGOING)
    private List<NodeEntity> nodes = new ArrayList<>();
}
