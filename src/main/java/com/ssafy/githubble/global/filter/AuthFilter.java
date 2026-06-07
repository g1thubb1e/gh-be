package com.ssafy.githubble.global.filter;

import com.ssafy.githubble.domain.auth.jwt.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RequiredArgsConstructor
public class AuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    private String getAuthentication(HttpServletRequest request){
        Cookie[] cookies = request.getCookies();
        if(cookies == null) return null;
        for (Cookie cookie : cookies) {
            if ("accessToken".equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String normalizedUri = uri.startsWith("/api") ? uri.substring(4) : uri;

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        if (isProtectedAuthEndpoint(request.getMethod(), normalizedUri)) {
            return false;
        }

        return WHITE_LIST.stream().anyMatch(normalizedUri::startsWith);
    }

    private static final List<String> WHITE_LIST = List.of(
            "/v1/auth",
            "/v1/ingest",
            "/v1/feedback",
            "/v1/admin",
            "/sse",
            "/v1/agent/qa-answer",
            "/v1/agent/route",
            "/swagger",
            "/v3/api-docs",
            "/actuator/health",
            "/actuator/prometheus"
    );

    private boolean isProtectedAuthEndpoint(String method, String normalizedUri) {
        return ("DELETE".equalsIgnoreCase(method)
                && normalizedUri.equals("/v1/auth/logout"))
                || ("GET".equalsIgnoreCase(method)
                && normalizedUri.equals("/v1/auth/withdrawal"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = this.getAuthentication(request);
        if (token == null || !jwtTokenProvider.validateToken(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("""
                {"status":"ERROR","message":"인증이 필요합니다.","data":null}
            """);
            return;
        }
        String userId = jwtTokenProvider.getContent(token);
        Long appuserId = Long.valueOf(userId);
        request.setAttribute("appuserId", appuserId);

        filterChain.doFilter(request, response);
    }
}
