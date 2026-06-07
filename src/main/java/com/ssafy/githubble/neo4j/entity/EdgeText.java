package com.ssafy.githubble.neo4j.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.ArrayList;
import java.util.List;

@Node("EdgeText")
@Getter
@Setter
public class EdgeText {

    @Id
    private String compositeId; // archId:edgeId

    private String archId;
    private String fromNodeId;
    private String toNodeId;
    private String label;
    private String description;
    private String style;

    @Relationship(type = "DESCRIBES_EDGE", direction = Relationship.Direction.OUTGOING)
    private List<NodeEntity> nodes = new ArrayList<>();
}
