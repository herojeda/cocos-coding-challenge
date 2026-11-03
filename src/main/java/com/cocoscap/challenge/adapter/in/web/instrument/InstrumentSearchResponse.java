package com.cocoscap.challenge.adapter.in.web.instrument;

import com.cocoscap.challenge.application.pagination.Page;
import com.cocoscap.challenge.domain.instrument.Instrument;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Paginated instrument search result")
record InstrumentSearchResponse(List<InstrumentItemResponse> instruments, PageInfoResponse page) {

    static InstrumentSearchResponse from(Page<Instrument> result) {
        List<InstrumentItemResponse> items =
                result.items().stream().map(InstrumentItemResponse::from).toList();
        return new InstrumentSearchResponse(
                items, new PageInfoResponse(result.number(), result.size(), result.hasNext()));
    }

    record InstrumentItemResponse(
            long id,
            String ticker,
            String name,
            @Schema(allowableValues = {"STOCK", "CURRENCY"}) String type) {

        static InstrumentItemResponse from(Instrument instrument) {
            return new InstrumentItemResponse(
                    instrument.id().value(),
                    instrument.ticker(),
                    instrument.name(),
                    instrument.type().name());
        }
    }
}

record PageInfoResponse(
        int number, int size, @Schema(description = "Whether a next page exists") boolean hasNext) {
}
