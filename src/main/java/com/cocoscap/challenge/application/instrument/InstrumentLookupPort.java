package com.cocoscap.challenge.application.instrument;

import com.cocoscap.challenge.domain.instrument.Instrument;
import com.cocoscap.challenge.domain.instrument.InstrumentId;
import java.util.Collection;
import java.util.Map;

/** Loads instruments by id for portfolio valuation and similar reads. */
public interface InstrumentLookupPort {

    /**
     * Returns each requested instrument that exists. Missing ids are omitted. An empty id
     * collection yields an empty map without querying.
     */
    Map<InstrumentId, Instrument> findByIds(Collection<InstrumentId> instrumentIds);
}
