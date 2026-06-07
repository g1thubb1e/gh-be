package com.ssafy.githubble.domain.ai.service;

import com.ssafy.githubble.domain.ai.component.QueryIntent;
import com.ssafy.githubble.domain.ai.component.RoutingAssistant;
import com.ssafy.githubble.global.util.LogSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoutingService {

    private final RoutingAssistant routingAssistant;

    public QueryIntent classify(String query) {
        String raw = routingAssistant.classify(query);
        String token = raw == null ? "" : raw.strip().toUpperCase();
        LogSanitizer.TextSummary querySummary = LogSanitizer.summarizeText(query);
        try {
            QueryIntent intent = QueryIntent.valueOf(token);
            log.info("ai.routing.event event=classified intent={} questionLength={} questionHash={}",
                    intent, querySummary.length(), querySummary.hash());
            return intent;
        } catch (IllegalArgumentException e) {
            LogSanitizer.TextSummary tokenSummary = LogSanitizer.summarizeText(raw);
            log.warn("ai.routing.event event=classification_fallback tokenLength={} tokenHash={} questionLength={} questionHash={} errorType={}",
                    tokenSummary.length(), tokenSummary.hash(), querySummary.length(), querySummary.hash(), e.getClass().getName());
            return QueryIntent.FALLBACK;
        }
    }
}
