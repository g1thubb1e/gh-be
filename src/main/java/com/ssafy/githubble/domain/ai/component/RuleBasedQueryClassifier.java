package com.ssafy.githubble.domain.ai.component;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class RuleBasedQueryClassifier {
    public QueryIntent classify(String question) {
        return QueryIntent.GROUP_DESCRIBE;
    }
}
