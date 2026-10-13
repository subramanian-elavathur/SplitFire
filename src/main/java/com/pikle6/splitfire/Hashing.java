package com.pikle6.splitfire;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/** SHA-256 helpers. */
final class Hashing {

    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

    private Hashing() {
    }

    static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // Every Java platform is required to support SHA-256.
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /** Completes the digest and returns it as lowercase hex. */
    static String hex(MessageDigest digest) {
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Validates a hex-encoded SHA-256 digest and returns it in lowercase. */
    static String requireSha256Hex(String value, String description) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        if (!SHA256_HEX.matcher(normalized).matches()) {
            throw new IllegalArgumentException("invalid " + description + ": \"" + value + "\" is not a SHA-256 hex digest");
        }
        return normalized;
    }
}
