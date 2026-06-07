package com.ssafy.githubble.neo4j.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("Group")
@Getter
@Setter
public class Group {

    @Id
    private String compositeId; // archId:groupId

    private String archId;
    private String groupId;
    private String label;
    private String description;
}
