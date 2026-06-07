package com.ssafy.githubble.global.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class LogSanitizer {

    private static final int HASH_HEX_LENGTH = 16;

    private LogSanitizer() {
    }

    public static TextSummary summarizeText(String text) {
        if (text == null) {
            return new TextSummary(0, "none");
        }
        return new TextSummary(text.length(), stableHash(text));
    }

    private static String stableHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed).substring(0, HASH_HEX_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", e);
        }
    }

    public record TextSummary(int length, String hash) {
        public String toLogString() {
            return "length=" + length + " hash=" + hash;
        }
    }
}
