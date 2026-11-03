package com.cocoscap.challenge.adapter.in.web.instrument;

import com.cocoscap.challenge.domain.instrument.InstrumentSort;
import io.swagger.v3.oas.annotations.media.Schema;

/** Query-param sort values for {@code GET /instruments}. */
@Schema(enumAsRef = true)
enum InstrumentSortRequest {
    TICKER,
    NAME;

    InstrumentSort toDomain() {
        return switch (this) {
            case TICKER -> InstrumentSort.TICKER;
            case NAME -> InstrumentSort.NAME;
        };
    }
}
