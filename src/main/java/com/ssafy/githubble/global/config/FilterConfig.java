package com.ssafy.githubble.global.config;

import com.ssafy.githubble.domain.auth.jwt.JwtTokenProvider;
import com.ssafy.githubble.global.filter.AccessLogFilter;
import com.ssafy.githubble.global.filter.AuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class FilterConfig {

    private final JwtTokenProvider jwtProvider;

    @Bean
    public FilterRegistrationBean<AccessLogFilter> accessLogFilter() {
        FilterRegistrationBean<AccessLogFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new AccessLogFilter());
        bean.setOrder(0);
        bean.addUrlPatterns("/*");
        return bean;
    }

    @Bean
    public FilterRegistrationBean<AuthFilter> authFilter() {
        AuthFilter filter = new AuthFilter(jwtProvider);
        FilterRegistrationBean<AuthFilter> bean =
                new FilterRegistrationBean<>();
        bean.setFilter(filter);
        bean.setOrder(1);
        bean.addUrlPatterns("/*");
        return bean;
    }
}
