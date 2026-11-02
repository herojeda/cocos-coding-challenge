package com.cocoscap.challenge.adapter.out.persistence.market;

import com.cocoscap.challenge.application.market.MarketDataPort;
import com.cocoscap.challenge.domain.instrument.InstrumentId;
import com.cocoscap.challenge.domain.market.Quote;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Latest quote per instrument via PostgreSQL {@code DISTINCT ON (instrument_id)} ordered by date
 * descending (ADR-002, S9).
 */
public class JdbcMarketDataAdapter implements MarketDataPort {

    private final JdbcClient jdbcClient;

    public JdbcMarketDataAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient");
    }

    @Override
    public Map<InstrumentId, Quote> findLatestByInstrumentIds(Collection<InstrumentId> instrumentIds) {
        Objects.requireNonNull(instrumentIds, "instrumentIds");
        if (instrumentIds.isEmpty()) {
            return Map.of();
        }

        List<Long> ids = instrumentIds.stream().map(InstrumentId::value).distinct().toList();

        List<QuoteRow> rows = jdbcClient
                .sql(
                        """
                        SELECT DISTINCT ON (instrument_id)
                            instrument_id, date, close, previous_close
                        FROM market_data
                        WHERE instrument_id IN (:ids)
                        ORDER BY instrument_id, date DESC
                        """)
                .param("ids", ids)
                .query(QuoteRow.class)
                .list();

        return rows.stream().collect(Collectors.toMap(row -> InstrumentId.of(row.instrumentId()), this::toDomain));
    }

    private Quote toDomain(QuoteRow row) {
        return new Quote(
                InstrumentId.of(row.instrumentId()), row.date(), row.close(), row.previousClose());
    }

    record QuoteRow(long instrumentId, LocalDate date, BigDecimal close, BigDecimal previousClose) {
    }
}
