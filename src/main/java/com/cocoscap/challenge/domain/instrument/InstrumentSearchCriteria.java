package com.cocoscap.challenge.domain.instrument;

import java.util.Optional;

/**
 * Explicit optional ticker/name filters combined with AND, plus a sort.
 * Both filters empty means the full searchable catalog.
 */
public record InstrumentSearchCriteria(
        Optional<InstrumentSearchText> ticker, Optional<InstrumentSearchText> name, InstrumentSort sort) {

    public InstrumentSearchCriteria {
        if (ticker == null) {
            throw new IllegalArgumentException("ticker filter is required");
        }
        if (name == null) {
            throw new IllegalArgumentException("name filter is required");
        }
        if (sort == null) {
            throw new IllegalArgumentException("sort is required");
        }
    }

    public static InstrumentSearchCriteria unfiltered() {
        return new InstrumentSearchCriteria(Optional.empty(), Optional.empty(), InstrumentSort.TICKER);
    }
}
