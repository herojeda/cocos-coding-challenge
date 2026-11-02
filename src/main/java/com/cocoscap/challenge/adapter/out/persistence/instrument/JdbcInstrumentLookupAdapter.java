package com.cocoscap.challenge.adapter.out.persistence.instrument;

import com.cocoscap.challenge.application.instrument.InstrumentLookupPort;
import com.cocoscap.challenge.domain.instrument.Instrument;
import com.cocoscap.challenge.domain.instrument.InstrumentId;
import com.cocoscap.challenge.domain.instrument.InstrumentType;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Loads instruments by primary key for portfolio valuation. */
public class JdbcInstrumentLookupAdapter implements InstrumentLookupPort {

    private final JdbcClient jdbcClient;

    public JdbcInstrumentLookupAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient");
    }

    @Override
    public Map<InstrumentId, Instrument> findByIds(Collection<InstrumentId> instrumentIds) {
        Objects.requireNonNull(instrumentIds, "instrumentIds");
        if (instrumentIds.isEmpty()) {
            return Map.of();
        }

        List<Long> ids = instrumentIds.stream().map(InstrumentId::value).distinct().toList();

        List<InstrumentRow> rows = jdbcClient
                .sql(
                        """
                        SELECT id, ticker, name, type
                        FROM instruments
                        WHERE id IN (:ids)
                        """)
                .param("ids", ids)
                .query(InstrumentRow.class)
                .list();

        return rows.stream().collect(Collectors.toMap(row -> InstrumentId.of(row.id()), this::toDomain));
    }

    private Instrument toDomain(InstrumentRow row) {
        return new Instrument(
                InstrumentId.of(row.id()), row.ticker(), row.name(), InstrumentType.valueOf(row.type()));
    }

    record InstrumentRow(long id, String ticker, String name, String type) {
    }
}
