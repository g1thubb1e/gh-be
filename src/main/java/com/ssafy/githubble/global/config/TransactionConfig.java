package com.ssafy.githubble.global.config;

import jakarta.persistence.EntityManagerFactory;
import org.neo4j.driver.Driver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.neo4j.core.DatabaseSelectionProvider;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 트랜잭션 관련 빈(Bean)을 구성하는 설정 클래스입니다.
 *
 * 이 클래스는 애플리케이션에서 Neo4j(그래프 DB)와 JPA(관계형 DB)를 동시에
 * 사용하는 경우 각 저장소에 맞는 트랜잭션 매니저를 제공하도록 구성합니다.
 *
 * 주요 제공 빈:
 * - JpaTransactionManager: JPA(EntityManagerFactory)를 사용해 관계형 DB 트랜잭션을 처리합니다.
 *   이 빈은 @Primary로 표시되어 기본 PlatformTransactionManager로 선택됩니다.
 * - Neo4jTransactionManager: Neo4j 드라이버와 DatabaseSelectionProvider를 사용하여 그래프 DB 트랜잭션을 처리합니다.
 * - TransactionTemplate: Neo4j 트랜잭션 매니저를 래핑해 프로그래매틱 트랜잭션 템플릿을 제공합니다.
 *
 * 주의사항:
 * - Neo4j를 사용하는 서비스에서는 @Transactional(transactionManager = "neo4jTransactionManager")를 명시해야 합니다.
 * - JPA를 사용하는 서비스에서는 @Transactional만 사용하면 기본(Primary) JPA 매니저가 선택됩니다.
 */
@Configuration
public class TransactionConfig {

    /**
     * JPA (관계형 DB) 전용 트랜잭션 매니저를 생성합니다.
     *
     * 파라미터:
     * - EntityManagerFactory: JPA 엔티티 매니저 팩토리. 이 팩토리를 통해 EntityManager와 트랜잭션을 관리합니다.
     *
     * 반환값:
     * - JpaTransactionManager: Spring의 JPA 트랜잭션 매니저 구현체
     *
     * 추가 설명:
     * - 이 빈에 @Primary를 붙여 PlatformTransactionManager 타입으로 주입될 때 기본값으로 사용되게 했습니다.
     *   JPA 기반 서비스에서 @Transactional만 사용하면 이 매니저가 선택됩니다.
     */
    @Bean
    @Primary
    public JpaTransactionManager jpaTransactionManager(EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }

    /**
     * Neo4j 전용 트랜잭션 매니저를 생성합니다.
     *
     * 파라미터:
     * - Driver: Neo4j Java 드라이버(연결 및 세션 생성 담당)
     * - DatabaseSelectionProvider: 사용할 Neo4j 데이터베이스(예: 멀티 DB 환경에서 DB 선택)를 제공
     *
     * 반환값:
     * - Neo4jTransactionManager: Spring Data Neo4j에서 사용되는 트랜잭션 매니저
     *
     * 사용 예:
     * - Neo4j 기반 서비스에서 @Transactional(transactionManager = "neo4jTransactionManager")로 지정하여 사용합니다.
     */
    @Bean
    public Neo4jTransactionManager neo4jTransactionManager(Driver driver,
                                                            DatabaseSelectionProvider dbProvider) {
        return new Neo4jTransactionManager(driver, dbProvider);
    }

    /**
     * 프로그래매틱 트랜잭션 관리를 위한 TransactionTemplate 빈을 생성합니다.
     *
     * 파라미터:
     * - @Qualifier("neo4jTransactionManager"): 명시적으로 Neo4j용 트랜잭션 매니저를 주입합니다.
     *
     * 반환값:
     * - TransactionTemplate: 주입된 PlatformTransactionManager를 사용해 트랜잭션을 실행할 수 있는 템플릿
     *
     * 추가 설명:
     * - 주입된 트랜잭션 매니저를 바꾸면(예: JPA 매니저) TransactionTemplate이 해당 매니저를 사용하게 됩니다.
     */
    @Bean
    public TransactionTemplate transactionTemplate(
            @Qualifier("neo4jTransactionManager") PlatformTransactionManager tm) {
        // 주입된 트랜잭션 매니저를 사용하여 TransactionTemplate 생성
        return new TransactionTemplate(tm);
    }

    @Bean(name = "jpaTransactionTemplate")
    public TransactionTemplate jpaTransactionTemplate(
            @Qualifier("jpaTransactionManager") PlatformTransactionManager tm) {
        return new TransactionTemplate(tm);
    }

}
