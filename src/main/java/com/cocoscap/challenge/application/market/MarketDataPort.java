package com.cocoscap.challenge.application.market;

import com.cocoscap.challenge.domain.instrument.InstrumentId;
import com.cocoscap.challenge.domain.market.Quote;
import java.util.Collection;
import java.util.Map;

/** Latest daily quote per instrument ({@code close} of the most recent {@code market_data} date). */
public interface MarketDataPort {

    /**
     * Returns the latest quote for each requested instrument that has at least one row. Instruments
     * without market data are omitted. An empty id collection yields an empty map without querying.
     */
    Map<InstrumentId, Quote> findLatestByInstrumentIds(Collection<InstrumentId> instrumentIds);
}
