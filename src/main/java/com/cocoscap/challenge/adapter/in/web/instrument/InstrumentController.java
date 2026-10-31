package com.cocoscap.challenge.adapter.in.web.instrument;

import com.cocoscap.challenge.application.instrument.SearchInstruments;
import com.cocoscap.challenge.application.paging.Page;
import com.cocoscap.challenge.application.paging.PageRequest;
import com.cocoscap.challenge.domain.instrument.Instrument;
import com.cocoscap.challenge.domain.instrument.InstrumentSearchText;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/instruments")
public class InstrumentController {

    private final SearchInstruments searchInstruments;

    public InstrumentController(SearchInstruments searchInstruments) {
        this.searchInstruments = searchInstruments;
    }

    @GetMapping
    InstrumentSearchResponse search(
            @RequestParam @NotBlank String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Page<Instrument> result =
                searchInstruments.execute(new InstrumentSearchText(search), new PageRequest(page, size));
        return InstrumentSearchResponse.from(result);
    }
}
