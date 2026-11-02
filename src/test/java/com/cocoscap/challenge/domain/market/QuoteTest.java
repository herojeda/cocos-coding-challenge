package com.cocoscap.challenge.domain.market;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cocoscap.challenge.domain.instrument.InstrumentId;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuoteTest {

    private static final InstrumentId PAMP = InstrumentId.of(47L);
    private static final LocalDate DATE = LocalDate.of(2023, 7, 14);

    @Test
    @DisplayName("Quote rejects zero or negative close")
    void rejectsNonPositiveClose() {
        assertThatThrownBy(() -> new Quote(PAMP, DATE, BigDecimal.ZERO, new BigDecimal("921.80")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Quote(PAMP, DATE, new BigDecimal("-1"), new BigDecimal("921.80")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Quote rejects zero or negative previousClose")
    void rejectsNonPositivePreviousClose() {
        assertThatThrownBy(() -> new Quote(PAMP, DATE, new BigDecimal("925.85"), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Quote(PAMP, DATE, new BigDecimal("925.85"), new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
