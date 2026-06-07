package com.ssafy.githubble.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;

/**
 * 리포지토리 구성 클래스입니다.
 *
 * 역할:
 * - Neo4j와 JPA용 리포지토리를 모두 활성화하여 서로 다른 저장소(그래프 DB / 관계형 DB)를
 *   같은 애플리케이션에서 사용할 수 있게 합니다.
 *
 * 주의사항:
 * - 아래 `basePackages` 값은 현재 예시 패키지("com.example.graphrag.repository.*")로 설정되어 있습니다.
 *   실제 프로젝트의 리포지토리 패키지 경로로 변경해야 정상 동작합니다.
 */
@Configuration

// Neo4j용 리포지토리를 활성화합니다.
// - basePackages: Neo4j 리포지토리 인터페이스가 위치한 패키지
// - transactionManagerRef: Neo4j 트랜잭션 매니저 빈 이름(예: neo4jTransactionManager)을 참조
@EnableNeo4jRepositories(
    basePackages = "com.ssafy.githubble.neo4j",
    transactionManagerRef = "neo4jTransactionManager"
)

// JPA(Hibernate 등)용 리포지토리를 활성화합니다.
// - basePackages: JPA 리포지토리 인터페이스가 위치한 패키지
// - transactionManagerRef: JPA 트랜잭션 매니저 빈 이름(예: jpaTransactionManager)을 참조
// - entityManagerFactoryRef: JPA EntityManagerFactory 빈 이름을 명시하여 여러 데이터 소스가
//   있는 경우에도 올바른 엔티티 관리자 팩토리를 사용하도록 합니다.
@EnableJpaRepositories(
    basePackages = "com.ssafy.githubble.domain",
    transactionManagerRef = "jpaTransactionManager",
    entityManagerFactoryRef = "entityManagerFactory"
)
public class RepositoriesConfig {
}
