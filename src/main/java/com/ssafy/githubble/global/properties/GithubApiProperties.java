package com.ssafy.githubble.global.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "github.api")
public class GithubApiProperties {

    private String baseUrl = "https://api.github.com";
    private String apiVersion = "2026-03-10";
}
