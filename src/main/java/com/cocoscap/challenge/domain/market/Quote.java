package com.cocoscap.challenge.domain.market;

import com.cocoscap.challenge.domain.instrument.InstrumentId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Daily market quote for one instrument. Prices must be positive, matching
 * {@code V6__market_data_positive_prices}.
 */
public record Quote(InstrumentId instrumentId, LocalDate date, BigDecimal close, BigDecimal previousClose) {

    public Quote {
        Objects.requireNonNull(instrumentId, "instrumentId");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(close, "close");
        Objects.requireNonNull(previousClose, "previousClose");
        if (close.signum() <= 0) {
            throw new IllegalArgumentException("close must be positive");
        }
        if (previousClose.signum() <= 0) {
            throw new IllegalArgumentException("previousClose must be positive");
        }
    }
}
