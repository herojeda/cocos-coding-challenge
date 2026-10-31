package com.cocoscap.challenge.application.instrument;

import com.cocoscap.challenge.application.paging.Page;
import com.cocoscap.challenge.application.paging.PageRequest;
import com.cocoscap.challenge.domain.instrument.Instrument;
import com.cocoscap.challenge.domain.instrument.InstrumentSearchText;
import org.springframework.transaction.annotation.Transactional;

/** Finds instruments by ticker or name. */
public class SearchInstruments {

    private final InstrumentSearchPort instrumentSearchPort;

    public SearchInstruments(InstrumentSearchPort instrumentSearchPort) {
        this.instrumentSearchPort = instrumentSearchPort;
    }

    @Transactional(readOnly = true)
    public Page<Instrument> execute(InstrumentSearchText text, PageRequest pageRequest) {
        return instrumentSearchPort.search(text, pageRequest);
    }
}
