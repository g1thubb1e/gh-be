package com.ssafy.githubble.domain.ai.component.retrieve;

import org.springframework.stereotype.Component;

@Component
public class LuceneQuerySanitizer {

    // Lucene QueryParser special characters
    private static final char[] SPECIAL_CHARS = {
        '+', '-', '&', '|', '!', '(', ')', '{', '}', '[', ']',
        '^', '"', '~', '*', '?', ':', '\\', '/'
    };

    public String escape(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }
        StringBuilder sb = new StringBuilder(input.length() * 2);
        for (char c : input.toCharArray()) {
            if (isSpecial(c)) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private boolean isSpecial(char c) {
        for (char special : SPECIAL_CHARS) {
            if (c == special) return true;
        }
        return false;
    }
}
