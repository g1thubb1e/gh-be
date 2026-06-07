package com.ssafy.githubble.neo4j.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.ArrayList;
import java.util.List;

@Node("Node")
@Getter
@Setter
public class NodeEntity {

    @Id
    private String compositeId; // archId:nodeId

    private String archId;
    private String nodeId;
    private String label;
    private String type;
    private String description;
    private String path;
    private String shape;
    private Boolean missingRef = false;
    private Integer degree = 0;

    @Relationship(type = "BELONGS_TO", direction = Relationship.Direction.OUTGOING)
    private Group group;

    @Relationship(type = "CONNECTS_TO", direction = Relationship.Direction.OUTGOING)
    private List<ConnectsTo> outgoing = new ArrayList<>();
}
