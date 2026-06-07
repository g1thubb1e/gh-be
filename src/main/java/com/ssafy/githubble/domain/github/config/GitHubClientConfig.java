package com.ssafy.githubble.domain.github.config;

import com.ssafy.githubble.global.properties.GithubApiProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class GitHubClientConfig {

    private static final int GITHUB_MAX_IN_MEMORY_SIZE = 10 * 1024 * 1024;

    @Bean
    public WebClient githubWebClient(GithubApiProperties properties) {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(GITHUB_MAX_IN_MEMORY_SIZE))
                .build();

        return WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .exchangeStrategies(strategies)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", properties.getApiVersion())
                .build();
    }
}
