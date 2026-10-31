package com.cocoscap.challenge.domain.instrument;

/**
 * Non-blank search text for instruments. Leading and trailing whitespace is trimmed.
 */
public record InstrumentSearchText(String value) {

    public InstrumentSearchText {
        if (value == null) {
            throw new IllegalArgumentException("search text is required");
        }
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("search text must not be blank");
        }
    }
}
