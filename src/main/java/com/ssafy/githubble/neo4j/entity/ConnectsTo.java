package com.ssafy.githubble.neo4j.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.RelationshipId;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

@RelationshipProperties
@Getter
@Setter
public class ConnectsTo {

    @RelationshipId
    private Long id;

    private String edgeId;
    private String label;
    private String description;
    private String style;

    @TargetNode
    private NodeEntity target;
}
