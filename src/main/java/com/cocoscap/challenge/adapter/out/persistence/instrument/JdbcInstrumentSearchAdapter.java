package com.cocoscap.challenge.adapter.out.persistence.instrument;

import com.cocoscap.challenge.application.instrument.InstrumentSearchPort;
import com.cocoscap.challenge.application.pagination.Page;
import com.cocoscap.challenge.application.pagination.Pagination;
import com.cocoscap.challenge.domain.instrument.Instrument;
import com.cocoscap.challenge.domain.instrument.InstrumentId;
import com.cocoscap.challenge.domain.instrument.InstrumentSearchCriteria;
import com.cocoscap.challenge.domain.instrument.InstrumentSearchText;
import com.cocoscap.challenge.domain.instrument.InstrumentType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Accent-insensitive instrument search excluding CURRENCY. Builds WHERE from fixed fragments that
 * carry their bind parameters; ORDER BY is a fixed fragment per {@code InstrumentSort}.
 */
public class JdbcInstrumentSearchAdapter implements InstrumentSearchPort {

    private final JdbcClient jdbcClient;

    public JdbcInstrumentSearchAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Page<Instrument> search(InstrumentSearchCriteria criteria, Pagination pagination) {
        SqlFragment where = where(criteria);
        String sql =
                """
                SELECT i.id, i.ticker, i.name, i.type
                FROM instruments i
                %s
                %s
                LIMIT :limit OFFSET :offset
                """
                        .formatted(where.sql(), orderBy(criteria));

        List<InstrumentRow> rows = jdbcClient
                .sql(sql)
                .params(where.params())
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

    private static SqlFragment where(InstrumentSearchCriteria criteria) {
        StringBuilder clause = new StringBuilder("WHERE i.type <> 'CURRENCY'");
        Map<String, Object> params = new LinkedHashMap<>();
        criteria.ticker().ifPresent(text -> {
            clause.append(
                    " AND unaccent(lower(i.ticker)) LIKE unaccent(lower(:tickerPattern)) ESCAPE '\\'");
            params.put("tickerPattern", containsPattern(text));
        });
        criteria.name().ifPresent(text -> {
            clause.append(
                    " AND unaccent(lower(i.name)) LIKE unaccent(lower(:namePattern)) ESCAPE '\\'");
            params.put("namePattern", containsPattern(text));
        });
        return new SqlFragment(clause.toString(), Map.copyOf(params));
    }

    private static String orderBy(InstrumentSearchCriteria criteria) {
        return switch (criteria.sort()) {
            case TICKER -> "ORDER BY i.ticker";
            case NAME -> "ORDER BY unaccent(lower(i.name)), i.ticker";
        };
    }

    private static String containsPattern(InstrumentSearchText text) {
        return "%" + escapeLikeMetacharacters(text.value()) + "%";
    }

    static String escapeLikeMetacharacters(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static Instrument toDomain(InstrumentRow row) {
        return new Instrument(
                InstrumentId.of(row.id()), row.ticker(), row.name(), InstrumentType.valueOf(row.type()));
    }

    private record SqlFragment(String sql, Map<String, Object> params) {
    }

    record InstrumentRow(long id, String ticker, String name, String type) {
    }
}
