package com.cocoscap.challenge.adapter.in.web.instrument;

import com.cocoscap.challenge.application.instrument.SearchInstrumentsUseCase;
import com.cocoscap.challenge.application.pagination.Pagination;
import com.cocoscap.challenge.domain.instrument.InstrumentSearchCriteria;
import com.cocoscap.challenge.domain.instrument.InstrumentSearchText;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/instruments")
@Tag(name = "Instruments")
public class InstrumentController {

    /** Rejects blank/whitespace-only values; {@code null} (absent param) is allowed. */
    private static final String NON_BLANK = ".*\\S.*";

    private final SearchInstrumentsUseCase searchInstrumentsUseCase;

    public InstrumentController(SearchInstrumentsUseCase searchInstrumentsUseCase) {
        this.searchInstrumentsUseCase = searchInstrumentsUseCase;
    }

    @GetMapping
    @Operation(
            summary = "Search instruments",
            description =
                    "Optional ticker and name filters (AND). Blank filter → 400. "
                            + "sort=TICKER|NAME (default TICKER); page≥0; size 1–100 (default 20).")
    @ApiResponse(responseCode = "200", description = "Page of matching instruments")
    @ApiResponse(
            responseCode = "400",
            description = "VALIDATION_ERROR (blank filter, invalid sort, page/size out of range)",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
    InstrumentSearchResponse search(
            @Parameter(description = "Substring on ticker") @RequestParam(required = false) @Pattern(regexp = NON_BLANK)
                    String ticker,
            @Parameter(description = "Substring on name") @RequestParam(required = false) @Pattern(regexp = NON_BLANK)
                    String name,
            @Parameter(description = "TICKER (default) or NAME") @RequestParam(defaultValue = "TICKER")
                    InstrumentSortRequest sort,
            @Parameter(description = "Zero-based page") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size 1–100") @RequestParam(defaultValue = "20") @Min(1) @Max(100)
                    int size) {
        InstrumentSearchCriteria criteria = new InstrumentSearchCriteria(
                Optional.ofNullable(ticker).map(InstrumentSearchText::new),
                Optional.ofNullable(name).map(InstrumentSearchText::new),
                sort.toDomain());
        return InstrumentSearchResponse.from(
                searchInstrumentsUseCase.execute(criteria, new Pagination(page, size)));
    }
}
