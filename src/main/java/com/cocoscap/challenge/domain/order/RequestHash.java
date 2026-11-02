package com.cocoscap.challenge.domain.order;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * SHA-256 fingerprint of a placement request (ADR-008), stored as 64 lowercase hex characters in
 * {@code orders.request_hash}.
 */
public record RequestHash(String value) {

    private static final int HEX_LENGTH = 64;

    public RequestHash {
        Objects.requireNonNull(value, "value");
        if (value.length() != HEX_LENGTH || !isLowerHex(value)) {
            throw new IllegalArgumentException("request hash must be 64 lowercase hex characters");
        }
    }

    public static RequestHash of(String value) {
        return new RequestHash(value);
    }

    public static RequestHash sha256(String canonical) {
        Objects.requireNonNull(canonical, "canonical");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return new RequestHash(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private static boolean isLowerHex(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c < '0' || c > '9') && (c < 'a' || c > 'f')) {
                return false;
            }
        }
        return true;
    }
}
