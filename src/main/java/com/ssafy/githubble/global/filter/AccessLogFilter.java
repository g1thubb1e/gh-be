package com.ssafy.githubble.global.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
public class AccessLogFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private static final String MDC_REQUEST_ID = "requestId";
    private static final String MDC_METHOD = "method";
    private static final String MDC_URI = "uri";
    private static final int MAX_REQUEST_ID_LENGTH = 128;
    private static final Pattern SAFE_REQUEST_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._-]+$");
    private static final Set<String> EXCLUDED_INFO_ACCESS_LOG_PATHS = Set.of(
            "/actuator/health",
            "/actuator/prometheus"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String normalizedUri = normalizeUri(request.getRequestURI());
        return EXCLUDED_INFO_ACCESS_LOG_PATHS.contains(normalizedUri);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.currentTimeMillis();
        String requestId = resolveRequestId(request);
        Throwable thrown = null;

        MDC.put(MDC_REQUEST_ID, requestId);
        MDC.put(MDC_METHOD, request.getMethod());
        MDC.put(MDC_URI, request.getRequestURI());
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException | Error e) {
            thrown = e;
            throw e;
        } finally {
            try {
                logAccess(request, response, requestId, startedAt, thrown);
            } finally {
                MDC.remove(MDC_URI);
                MDC.remove(MDC_METHOD);
                MDC.remove(MDC_REQUEST_ID);
            }
        }
    }

    private void logAccess(HttpServletRequest request,
                           HttpServletResponse response,
                           String requestId,
                           long startedAt,
                           Throwable thrown) {
        int status = resolveStatus(response, thrown);
        String exceptionType = thrown == null ? "-" : thrown.getClass().getName();
        String errorCode = thrown == null ? "-" : "INTERNAL_SERVER_ERROR";
        String appuserId = resolveAppuserId(request.getAttribute("appuserId"));

        log.info(
                "http.access requestId={} method={} uri={} status={} elapsedMs={} appuserId={} errorCode={} exceptionType={}",
                requestId,
                request.getMethod(),
                request.getRequestURI(),
                status,
                System.currentTimeMillis() - startedAt,
                appuserId,
                errorCode,
                exceptionType
        );
    }

    private int resolveStatus(HttpServletResponse response, Throwable thrown) {
        if (thrown != null && response.getStatus() < 400) {
            return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }
        return response.getStatus();
    }

    private String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            return UUID.randomUUID().toString();
        }

        String strippedRequestId = requestId.strip();
        if (strippedRequestId.length() > MAX_REQUEST_ID_LENGTH
                || !SAFE_REQUEST_ID_PATTERN.matcher(strippedRequestId).matches()) {
            return UUID.randomUUID().toString();
        }
        return strippedRequestId;
    }

    private String resolveAppuserId(Object appuserId) {
        if (appuserId instanceof Number number) {
            return String.valueOf(number.longValue());
        }
        return "-";
    }

    private String normalizeUri(String uri) {
        return uri.startsWith("/api") ? uri.substring(4) : uri;
    }
}
