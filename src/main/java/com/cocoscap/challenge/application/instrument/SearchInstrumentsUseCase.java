package com.cocoscap.challenge.application.instrument;

import com.cocoscap.challenge.application.pagination.Page;
import com.cocoscap.challenge.application.pagination.Pagination;
import com.cocoscap.challenge.domain.instrument.Instrument;
import com.cocoscap.challenge.domain.instrument.InstrumentSearchCriteria;
import org.springframework.transaction.annotation.Transactional;

/** Finds instruments by optional ticker and/or name filters. */
public class SearchInstrumentsUseCase {

    private final InstrumentSearchPort instrumentSearchPort;

    public SearchInstrumentsUseCase(InstrumentSearchPort instrumentSearchPort) {
        this.instrumentSearchPort = instrumentSearchPort;
    }

    @Transactional(readOnly = true)
    public Page<Instrument> execute(InstrumentSearchCriteria criteria, Pagination pagination) {
        return instrumentSearchPort.search(criteria, pagination);
    }
}
