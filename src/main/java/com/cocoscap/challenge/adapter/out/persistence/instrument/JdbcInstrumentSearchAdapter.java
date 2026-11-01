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
 * Accent-insensitive instrument search excluding CURRENCY. Builds WHERE/ORDER BY from fixed
 * fragments that carry their own bind parameters; uses size+1 for hasNext.
 */
public class JdbcInstrumentSearchAdapter implements InstrumentSearchPort {

    private final JdbcClient jdbcClient;

    public JdbcInstrumentSearchAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Page<Instrument> search(InstrumentSearchCriteria criteria, Pagination pagination) {
        SqlFragment where = where(criteria);
        SqlFragment orderBy = orderBy(criteria);
        String sql =
                """
                SELECT i.id, i.ticker, i.name, i.type
                FROM instruments i
                %s
                %s
                LIMIT :limit OFFSET :offset
                """
                        .formatted(where.sql(), orderBy.sql());

        List<InstrumentRow> rows = jdbcClient
                .sql(sql)
                .params(where.params())
                .params(orderBy.params())
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

    private static SqlFragment orderBy(InstrumentSearchCriteria criteria) {
        return switch (criteria.sort()) {
            case RELEVANCE -> relevanceOrder(criteria);
            case TICKER -> new SqlFragment("ORDER BY i.ticker", Map.of());
            case NAME -> new SqlFragment("ORDER BY unaccent(lower(i.name)), i.ticker", Map.of());
        };
    }

    private static SqlFragment relevanceOrder(InstrumentSearchCriteria criteria) {
        if (criteria.ticker().isPresent()) {
            InstrumentSearchText text = criteria.ticker().orElseThrow();
            return new SqlFragment(
                    """
                    ORDER BY
                      CASE
                        WHEN unaccent(lower(i.ticker)) = unaccent(lower(:tickerExact)) THEN 0
                        WHEN unaccent(lower(i.ticker)) LIKE unaccent(lower(:tickerPrefix)) ESCAPE '\\'
                          THEN 1
                        ELSE 2
                      END,
                      i.ticker
                    """,
                    Map.of(
                            "tickerExact", text.value(),
                            "tickerPrefix", prefixPattern(text)));
        }
        if (criteria.name().isPresent()) {
            InstrumentSearchText text = criteria.name().orElseThrow();
            return new SqlFragment(
                    """
                    ORDER BY
                      CASE
                        WHEN unaccent(lower(i.name)) = unaccent(lower(:nameExact)) THEN 0
                        WHEN unaccent(lower(i.name)) LIKE unaccent(lower(:namePrefix)) ESCAPE '\\'
                          THEN 1
                        ELSE 2
                      END,
                      i.ticker
                    """,
                    Map.of(
                            "nameExact", text.value(),
                            "namePrefix", prefixPattern(text)));
        }
        return new SqlFragment("ORDER BY i.ticker", Map.of());
    }

    private static String containsPattern(InstrumentSearchText text) {
        return "%" + escapeLikeMetacharacters(text.value()) + "%";
    }

    private static String prefixPattern(InstrumentSearchText text) {
        return escapeLikeMetacharacters(text.value()) + "%";
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
