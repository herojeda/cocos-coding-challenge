package com.cocoscap.challenge.adapter.out.persistence.market;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocoscap.challenge.PostgresTestcontainersConfiguration;
import com.cocoscap.challenge.application.market.MarketDataPort;
import com.cocoscap.challenge.domain.instrument.InstrumentId;
import com.cocoscap.challenge.domain.market.Quote;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
@Transactional
class JdbcMarketDataAdapterIT {

    private static final InstrumentId PAMP = InstrumentId.of(47L);
    private static final InstrumentId PGR = InstrumentId.of(3L);

    @Autowired
    private MarketDataPort marketDataPort;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    @DisplayName("PF-05 · Se usa la cotización más reciente")
    void pf05UsesMostRecentQuote() {
        // Seed has PAMP close 921.80 on 2023-07-13 and 925.85 on 2023-07-14.
        Map<InstrumentId, Quote> quotes = marketDataPort.findLatestByInstrumentIds(Set.of(PAMP));

        Quote pamp = quotes.get(PAMP);
        assertThat(pamp).isNotNull();
        assertThat(pamp.date()).isEqualTo(LocalDate.of(2023, 7, 14));
        assertThat(pamp.close()).isEqualByComparingTo("925.85");
        assertThat(pamp.previousClose()).isEqualByComparingTo("921.80");
    }

    @Test
    @DisplayName("Latest quotes omit instruments without market data")
    void omitsInstrumentsWithoutQuotes() {
        Map<InstrumentId, Quote> quotes = marketDataPort.findLatestByInstrumentIds(Set.of(PAMP, PGR));

        assertThat(quotes).containsOnlyKeys(PAMP);
    }

    @Test
    @DisplayName("Empty instrument id collection returns empty map without querying")
    void emptyIdsReturnEmptyMap() {
        assertThat(marketDataPort.findLatestByInstrumentIds(List.of())).isEmpty();
    }

    @Test
    @DisplayName("Latest quote follows newly inserted more recent date")
    void newlyInsertedLaterDateWins() {
        jdbcClient
                .sql(
                        """
                        INSERT INTO market_data (
                            instrument_id, date, open, high, low, close, previous_close
                        ) VALUES (
                            :instrumentId, '2023-07-15', 930, 940, 920, 935.50, 925.85
                        )
                        """)
                .param("instrumentId", PAMP.value())
                .update();

        Quote pamp = marketDataPort.findLatestByInstrumentIds(Set.of(PAMP)).get(PAMP);

        assertThat(pamp.date()).isEqualTo(LocalDate.of(2023, 7, 15));
        assertThat(pamp.close()).isEqualByComparingTo("935.50");
    }
}
