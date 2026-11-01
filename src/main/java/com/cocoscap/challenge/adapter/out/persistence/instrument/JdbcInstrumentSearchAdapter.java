package com.cocoscap.challenge.adapter.out.persistence.instrument;

import com.cocoscap.challenge.application.instrument.InstrumentSearchPort;
import com.cocoscap.challenge.application.pagination.Page;
import com.cocoscap.challenge.application.pagination.Pagination;
import com.cocoscap.challenge.domain.instrument.Instrument;
import com.cocoscap.challenge.domain.instrument.InstrumentId;
import com.cocoscap.challenge.domain.instrument.InstrumentSearchText;
import com.cocoscap.challenge.domain.instrument.InstrumentType;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Accent-insensitive instrument search excluding CURRENCY; uses size+1 for hasNext. */
public class JdbcInstrumentSearchAdapter implements InstrumentSearchPort {

    private static final String SEARCH_SQL =
            """
            SELECT i.id, i.ticker, i.name, i.type
            FROM instruments i
            WHERE i.type <> 'CURRENCY'
              AND (
                unaccent(lower(i.ticker)) LIKE unaccent(lower(:containsPattern)) ESCAPE '\\'
                OR unaccent(lower(i.name)) LIKE unaccent(lower(:containsPattern)) ESCAPE '\\'
              )
            ORDER BY
              CASE
                WHEN unaccent(lower(i.ticker)) = unaccent(lower(:exactText)) THEN 0
                WHEN unaccent(lower(i.ticker)) LIKE unaccent(lower(:prefixPattern)) ESCAPE '\\'
                  THEN 1
                ELSE 2
              END,
              i.ticker
            LIMIT :limit OFFSET :offset
            """;

    private final JdbcClient jdbcClient;

    public JdbcInstrumentSearchAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Page<Instrument> search(InstrumentSearchText text, Pagination pagination) {
        String escaped = escapeLikeMetacharacters(text.value());
        List<InstrumentRow> rows = jdbcClient
                .sql(SEARCH_SQL)
                .param("containsPattern", "%" + escaped + "%")
                .param("prefixPattern", escaped + "%")
                .param("exactText", text.value())
                .param("limit", pagination.size() + 1)
                .param("offset", pagination.offset())
                .query(InstrumentRow.class)
                .list();

        boolean hasNext = rows.size() > pagination.size();
        List<Instrument> items = rows.stream()
                .limit(pagination.size())
                .map(JdbcInstrumentSearchAdapter::toDomain)
                .toList();
        return new Page<>(items, pagination.page(), pagination.size(), hasNext);
    }

    static String escapeLikeMetacharacters(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static Instrument toDomain(InstrumentRow row) {
        return new Instrument(
                InstrumentId.of(row.id()), row.ticker(), row.name(), InstrumentType.valueOf(row.type()));
    }

    record InstrumentRow(long id, String ticker, String name, String type) {
    }
}
