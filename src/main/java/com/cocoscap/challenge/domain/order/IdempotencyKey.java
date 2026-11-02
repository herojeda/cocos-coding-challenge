package com.cocoscap.challenge.domain.order;

/**
 * Client-supplied placement key (ADR-008): 1–64 printable ASCII characters ({@code 0x21}–{@code 0x7E}).
 * Invalid values raise {@link NewOrderValidationException} on field {@code Idempotency-Key} without
 * echoing the value.
 */
public record IdempotencyKey(String value) {

    private static final int MIN_LENGTH = 1;
    private static final int MAX_LENGTH = 64;
    private static final char MIN_PRINTABLE = 0x21;
    private static final char MAX_PRINTABLE = 0x7E;

    public IdempotencyKey {
        if (value == null || value.length() < MIN_LENGTH || value.length() > MAX_LENGTH || !isPrintableAscii(value)) {
            throw new NewOrderValidationException("Idempotency-Key", "Idempotency-Key is invalid");
        }
    }

    public static IdempotencyKey of(String value) {
        return new IdempotencyKey(value);
    }

    private static boolean isPrintableAscii(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < MIN_PRINTABLE || c > MAX_PRINTABLE) {
                return false;
            }
        }
        return true;
    }
}
