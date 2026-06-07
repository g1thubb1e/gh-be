package com.ssafy.githubble.global.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogSanitizerTest {

    @Test
    @DisplayName("민감 텍스트는 원문 대신 길이와 안정적인 hash로 요약한다")
    void summarizesTextWithLengthAndHash() {
        LogSanitizer.TextSummary summary = LogSanitizer.summarizeText("secret question");

        assertThat(summary.length()).isEqualTo(15);
        assertThat(summary.hash()).isEqualTo("4832419824abfc95");
        assertThat(LogSanitizer.summarizeText("secret question").hash()).isEqualTo(summary.hash());
        assertThat(summary.toLogString())
                .contains("length=15")
                .contains("hash=")
                .doesNotContain("secret question");
    }

    @Test
    @DisplayName("null 텍스트는 길이 0과 none hash로 요약한다")
    void summarizesNullText() {
        LogSanitizer.TextSummary summary = LogSanitizer.summarizeText(null);

        assertThat(summary.length()).isZero();
        assertThat(summary.hash()).isEqualTo("none");
    }
}
