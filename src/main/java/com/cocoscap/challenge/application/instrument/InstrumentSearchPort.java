package com.cocoscap.challenge.application.instrument;

import com.cocoscap.challenge.application.pagination.Page;
import com.cocoscap.challenge.application.pagination.Pagination;
import com.cocoscap.challenge.domain.instrument.Instrument;
import com.cocoscap.challenge.domain.instrument.InstrumentSearchCriteria;

/** Outbound port for searching tradable instruments. */
public interface InstrumentSearchPort {

    Page<Instrument> search(InstrumentSearchCriteria criteria, Pagination pagination);
}
