package com.ssafy.githubble.global.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "GitHubble Backend API",
                version = "v1",
                description = "GitHub 저장소 시각화와 AI 탐색을 위한 백엔드 API 문서입니다. 인증은 GitHub OAuth 로그인 후 발급되는 HttpOnly Cookie 기반 accessToken을 사용합니다. OAuth 로그인/탈퇴 시작 API는 Swagger Execute보다 브라우저 주소창 직접 호출 방식으로 테스트하는 것이 적합합니다.",
                contact = @Contact(name = "GitHubble Backend")
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Local backend"),
                @Server(url = "https://githubble.site", description = "Remote backend")
        }
)
@SecurityScheme(
        name = "cookieAuth",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.COOKIE,
        paramName = "accessToken",
        description = "GitHub OAuth 로그인 후 백엔드가 발급하는 HttpOnly accessToken 쿠키입니다."
)
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI();
    }

    @Bean
    public OpenApiCustomizer openApiCustomizer() {
        return openApi -> {
            normalizeApiPaths(openApi);
            applySchemaExamples(openApi);
        };
    }

    private void normalizeApiPaths(OpenAPI openApi) {
        Paths paths = openApi.getPaths();
        if (paths == null || paths.isEmpty()) {
            return;
        }

        Paths normalizedPaths = new Paths();
        paths.forEach((path, pathItem) -> normalizedPaths.addPathItem("/api" + path, pathItem));
        openApi.setPaths(normalizedPaths);
    }

    private void applySchemaExamples(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return;
        }

        Map<String, Schema> schemas = openApi.getComponents().getSchemas();
        schemas.forEach((name, schema) -> {
            if (name.startsWith("ApiResult")) {
                schema.setExample(Map.of(
                        "status", "SUCCESS",
                        "message", "요청이 성공",
                        "data", Map.of("archId", "spring-projects__spring-petclinic")
                ));
            }
        });
        putExample(schemas, "GitHubRepositoryParseRequest", Map.of(
                "owner", "spring-projects",
                "repo", "spring-petclinic"
        ));
        putExample(schemas, "ParsedRepositoryResponse", Map.ofEntries(
                Map.entry("owner", "spring-projects"),
                Map.entry("repo", "spring-petclinic"),
                Map.entry("fullName", "spring-projects/spring-petclinic"),
                Map.entry("htmlUrl", "https://github.com/spring-projects/spring-petclinic"),
                Map.entry("description", "A sample Spring-based application"),
                Map.entry("defaultBranch", "main"),
                Map.entry("primaryLanguage", "Java"),
                Map.entry("stars", 8100),
                Map.entry("forks", 26000),
                Map.entry("openIssueCount", 85),
                Map.entry("languages", Map.of("Java", 152340, "HTML", 18320, "CSS", 6410)),
                Map.entry("treeTruncated", false),
                Map.entry("treeFiles", java.util.List.of(Map.of("path", "src/main/java/org/springframework/samples/petclinic/PetClinicApplication.java", "type", "blob", "size", 834, "extension", "java"))),
                Map.entry("readme", Map.of("path", "README.md", "content", "# Spring PetClinic\n\nSample Spring Boot application.", "encoding", "base64")),
                Map.entry("configFiles", java.util.List.of(Map.of("path", "build.gradle", "category", "gradle"))),
                Map.entry("warnings", java.util.List.of("README 파일은 기본 브랜치 기준으로 조회되었습니다.")),
                Map.entry("parsedAt", "2026-05-04T10:15:30")
        ));
        putExample(schemas, "QueryRequest", Map.of(
                "question", "이 저장소의 인증 흐름은 어떤 파일에서 시작하나요?",
                "repoUuid", "11111111-1111-1111-1111-111111111111"
        ));
        putExample(schemas, "IngestRequest", Map.of(
                "username", "spring-projects",
                "repo", "spring-petclinic",
                "attempt", "main-20260504-101530",
                "graph", Map.of(
                        "groups", java.util.List.of(Map.of("id", "backend", "label", "Backend", "description", "Spring Boot application layer")),
                        "nodes", java.util.List.of(Map.of("id", "app", "groupId", "backend", "label", "PetClinicApplication", "type", "class", "description", "Spring Boot entry point", "path", "src/main/java/org/springframework/samples/petclinic/PetClinicApplication.java", "shape", "circle")),
                        "edges", java.util.List.of(Map.of("id", "app-to-controller", "from", "app", "to", "owner-controller", "label", "initializes", "description", "Application context wires MVC controllers", "style", "solid")),
                        "rawText", "Spring PetClinic is a Spring Boot sample application."
                )
        ));
    }

    private void putExample(Map<String, Schema> schemas, String schemaName, Object example) {
        Schema schema = schemas.get(schemaName);
        if (schema != null) {
            schema.setExample(example);
        }
    }
}
