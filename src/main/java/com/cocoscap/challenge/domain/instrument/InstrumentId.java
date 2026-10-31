package com.cocoscap.challenge.domain.instrument;

/** Identity of a tradable instrument. */
public record InstrumentId(long value) {

    public InstrumentId {
        if (value <= 0) {
            throw new IllegalArgumentException("InstrumentId must be positive");
        }
    }

    public static InstrumentId of(long value) {
        return new InstrumentId(value);
    }
}
