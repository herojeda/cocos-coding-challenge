package com.cocoscap.challenge.adapter.in.web.instrument;

import com.cocoscap.challenge.domain.instrument.InstrumentSort;

/** Query-param sort values for {@code GET /instruments}. */
enum InstrumentSortRequest {
    TICKER,
    NAME;

    InstrumentSort toDomain() {
        return InstrumentSort.valueOf(name());
    }
}
