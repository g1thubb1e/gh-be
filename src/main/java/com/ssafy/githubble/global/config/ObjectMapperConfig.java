package com.ssafy.githubble.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 전역적으로 사용될 Jackson {@link ObjectMapper}를 구성하는 설정 클래스입니다.
 *
 * 이유:
 * Spring Boot는 기본 ObjectMapper를 제공하지만, 프로젝트에서 Java 8+ 날짜/시간 타입
 * 직렬화 설정이나 기타 직렬화 옵션을 명시적으로 제어하려면 커스텀 빈으로 등록합니다.
 *
 * 이 구성에서 하는 일:
 * - JavaTimeModule 등록: java.time.* 타입을 ISO 형식으로 (또는 커스터마이징 가능한 방식으로)
 *   직렬화/역직렬화할 수 있게 합니다.
 * - WRITE_DATES_AS_TIMESTAMPS 비활성화: 날짜를 타임스탬프 숫자가 아닌 문자열(예: ISO-8601)로 직렬화합니다.
 * - FAIL_ON_EMPTY_BEANS 비활성화: 빈(프로퍼티가 없는 POJO)을 직렬화할 때 예외가 발생하지 않도록 합니다.
 */
@Configuration
public class ObjectMapperConfig {

    /**
     * 애플리케이션에서 재사용할 ObjectMapper 빈을 생성합니다.
     *
     * 반환값:
     * - 구성된 {@link ObjectMapper} 인스턴스
     *
     * 주의사항 / 부가설명:
     * - JavaTimeModule 등록으로 LocalDate, LocalDateTime 등 java.time 타입의
     *   직렬화/역직렬화를 지원합니다.
     * - WRITE_DATES_AS_TIMESTAMPS를 비활성화하면 날짜/시간이 숫자 배열이나 epoch가
     *   아닌 사람이 읽을 수 있는 ISO 문자열로 직렬화됩니다.
     * - FAIL_ON_EMPTY_BEANS를 비활성화하면 Jackson이 직렬화할 프로퍼티가 전혀 없는
     *   빈 객체에 대해 예외를 던지지 않습니다(특정 응답에서 빈 객체를 안전하게 반환할 때 유용).
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Java 8 날짜/시간 타입을 처리하는 모듈 등록
        mapper.registerModule(new JavaTimeModule());

        // 날짜/시간을 타임스탬프(숫자)가 아닌 ISO-8601 문자열로 출력
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 빈 객체 직렬화 시 예외를 발생시키지 않도록 설정
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

        return mapper;
    }
}
