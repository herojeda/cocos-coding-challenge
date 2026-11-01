package com.cocoscap.challenge.domain.instrument;

/** Tradable (or cash) instrument from the market catalog. */
public record Instrument(InstrumentId id, String ticker, String name, InstrumentType type) {

    public Instrument {
        if (id == null) {
            throw new IllegalArgumentException("id is required");
        }
        if (ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("ticker is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }
    }
}
