package com.ssafy.githubble.global.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "github.oauth")
public class GithubOAuthProperties {
    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String scope = "read:user user:email";
    private String authorizeUrl;
}
