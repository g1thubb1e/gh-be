package com.ssafy.githubble.global.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ai-server.api")
public class AiServerProperties {
    String baseUrl;
    String setupPath;
}
