package com.ssafy.githubble.domain.ai.component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 애플리케이션 시작 시 Neo4j 데이터베이스에서 필요한 제약조건(constraints)과
 * 풀텍스트 인덱스(fulltext indexes)를 초기화하는 컴포넌트입니다.
 *
 * 동작 요약:
 * - Spring의 ApplicationRunner를 구현하여 애플리케이션 시작 후 자동으로 실행됩니다.
 * - Neo4j 드라이버(Driver)를 통해 세션(Session)을 열고 Cypher 구문을 실행합니다.
 * - Cypher 구문은 모두 "IF NOT EXISTS" 절을 사용하므로, 이미 존재하면 중복 생성하지 않습니다.
 *
 * 주요 책임:
 * - 도메인 엔티티(Architecture, Group, Node, EdgeText)에 대한 UNIQUE 제약조건을 생성
 * - Node/Group/EdgeText에 대한 풀텍스트 색인 생성 (검색 성능 향상 목적)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Neo4jIndexInitializer implements ApplicationRunner {

    private final Driver driver;

    @Value("${graphrag.embedding.dimension}")
    private int embeddingDimension;

    /**
     * 애플리케이션 시작 시 실행되는 진입점입니다.
     * 세션을 열어 제약조건 및 풀텍스트 인덱스 생성 메서드를 호출합니다.
     */
    @Override
    public void run(ApplicationArguments args) {
        log.info("Initializing Neo4j constraints and fulltext indexes...");
        // try-with-resources를 사용해 Session을 열고 자동으로 자원을 해제합니다.
        try (Session session = driver.session()) {
            createConstraints(session);
            //createFulltextIndexes(session);
            createVectorIndexes(session);
            createPropertyIndexes(session);
        }
        log.info("Neo4j initialization complete.");
    }

    /**
     * 각 도메인 노드에 대해 UNIQUE 제약조건을 생성합니다.
     *
     * 중요한 점:
     * - CREATE CONSTRAINT ... IF NOT EXISTS를 사용해 이미 존재하면 아무런 영향을 주지 않습니다.
     * - 제약조건은 노드의 특정 프로퍼티를 고유 식별자로 강제합니다.
     */
    private void createConstraints(Session session) {
        // Architecture 노드의 id 필드를 고유 제약조건으로 설정
        session.run("CREATE CONSTRAINT arch_id IF NOT EXISTS FOR (a:Architecture) REQUIRE a.id IS UNIQUE");

        // Group 노드의 compositeId 필드를 고유 제약조건으로 설정
        session.run("CREATE CONSTRAINT group_cid IF NOT EXISTS FOR (g:Group) REQUIRE g.compositeId IS UNIQUE");

        // Node 노드의 compositeId 필드를 고유 제약조건으로 설정
        session.run("CREATE CONSTRAINT node_cid IF NOT EXISTS FOR (n:Node) REQUIRE n.compositeId IS UNIQUE");

        // EdgeText 노드의 compositeId 필드를 고유 제약조건으로 설정
        session.run("CREATE CONSTRAINT edge_cid IF NOT EXISTS FOR (e:EdgeText) REQUIRE e.compositeId IS UNIQUE");
    }

    private void createPropertyIndexes(Session session) {
        for (String cypher : new String[]{
                // Node: 복합 인덱스가 단일 필드 쿼리도 커버하므로 단일 인덱스 미포함
                "CREATE INDEX idx_node_arch_nid   IF NOT EXISTS FOR (n:Node) ON (n.archId, n.nodeId)",
                "CREATE INDEX idx_node_arch_deg   IF NOT EXISTS FOR (n:Node) ON (n.archId, n.degree)",
                "CREATE INDEX idx_node_node_id    IF NOT EXISTS FOR (n:Node) ON (n.nodeId)",
                // Group: 복합 인덱스 + groupId 단일 (groupId 단독 조회 경로 존재)
                "CREATE INDEX idx_group_arch_gid  IF NOT EXISTS FOR (g:Group) ON (g.archId, g.groupId)",
                "CREATE INDEX idx_group_group_id  IF NOT EXISTS FOR (g:Group) ON (g.groupId)",
                // EdgeText: archId 스코핑, 노드 역참조
                "CREATE INDEX idx_et_arch_id      IF NOT EXISTS FOR (et:EdgeText) ON (et.archId)",
                "CREATE INDEX idx_et_from_node    IF NOT EXISTS FOR (et:EdgeText) ON (et.fromNodeId)",
                "CREATE INDEX idx_et_to_node      IF NOT EXISTS FOR (et:EdgeText) ON (et.toNodeId)"
        }) {
            session.run(cypher);
        }
    }

    private void createVectorIndexes(Session session) {
        String options = String.format(
                "{indexConfig: {`vector.dimensions`: %d, `vector.similarity_function`: 'cosine'}}",
                embeddingDimension);

        for (String cypher : new String[]{
                "CREATE VECTOR INDEX node_embedding_idx IF NOT EXISTS FOR (n:Node) ON (n.embedding) OPTIONS " + options,
                "CREATE VECTOR INDEX group_embedding_idx IF NOT EXISTS FOR (g:Group) ON (g.embedding) OPTIONS " + options,
                "CREATE VECTOR INDEX edge_embedding_idx IF NOT EXISTS FOR (e:EdgeText) ON (e.embedding) OPTIONS " + options
        }) {
            session.run(cypher);
        }
    }

}
