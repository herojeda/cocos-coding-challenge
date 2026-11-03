package com.cocoscap.challenge.domain.instrument;

/** Raised when a placement references an instrument that does not exist (ORD-07). */
public final class InstrumentNotFoundException extends RuntimeException {

    public InstrumentNotFoundException() {
        super("instrument not found");
    }
}
