package com.cocoscap.challenge.adapter.in.web.instrument;

import com.cocoscap.challenge.application.instrument.SearchInstrumentsUseCase;
import com.cocoscap.challenge.application.pagination.Pagination;
import com.cocoscap.challenge.domain.instrument.InstrumentSearchCriteria;
import com.cocoscap.challenge.domain.instrument.InstrumentSearchText;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/instruments")
public class InstrumentController {

    /** Rejects blank/whitespace-only values; {@code null} (absent param) is allowed. */
    private static final String NON_BLANK = ".*\\S.*";

    private final SearchInstrumentsUseCase searchInstrumentsUseCase;

    public InstrumentController(SearchInstrumentsUseCase searchInstrumentsUseCase) {
        this.searchInstrumentsUseCase = searchInstrumentsUseCase;
    }

    @GetMapping
    InstrumentSearchResponse search(
            @RequestParam(required = false) @Pattern(regexp = NON_BLANK) String ticker,
            @RequestParam(required = false) @Pattern(regexp = NON_BLANK) String name,
            @RequestParam(defaultValue = "TICKER") InstrumentSortRequest sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        InstrumentSearchCriteria criteria = new InstrumentSearchCriteria(
                Optional.ofNullable(ticker).map(InstrumentSearchText::new),
                Optional.ofNullable(name).map(InstrumentSearchText::new),
                sort.toDomain());
        return InstrumentSearchResponse.from(
                searchInstrumentsUseCase.execute(criteria, new Pagination(page, size)));
    }
}
