package com.cocoscap.challenge.adapter.out.persistence.instrument;

import com.cocoscap.challenge.application.instrument.InstrumentSearchPort;
import com.cocoscap.challenge.application.pagination.Page;
import com.cocoscap.challenge.application.pagination.Pagination;
import com.cocoscap.challenge.domain.instrument.Instrument;
import com.cocoscap.challenge.domain.instrument.InstrumentId;
import com.cocoscap.challenge.domain.instrument.InstrumentSearchCriteria;
import com.cocoscap.challenge.domain.instrument.InstrumentSearchText;
import com.cocoscap.challenge.domain.instrument.InstrumentSort;
import com.cocoscap.challenge.domain.instrument.InstrumentType;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;

/**
 * Accent-insensitive instrument search excluding CURRENCY. Builds WHERE/ORDER BY from fixed
 * fragments per present filter and sort; uses size+1 for hasNext.
 */
public class JdbcInstrumentSearchAdapter implements InstrumentSearchPort {

    private final JdbcClient jdbcClient;

    public JdbcInstrumentSearchAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Page<Instrument> search(InstrumentSearchCriteria criteria, Pagination pagination) {
        String sql =
                """
                SELECT i.id, i.ticker, i.name, i.type
                FROM instruments i
                %s
                %s
                LIMIT :limit OFFSET :offset
                """
                        .formatted(where(criteria), orderBy(criteria));

        StatementSpec statement = jdbcClient
                .sql(sql)
                .param("limit", pagination.size() + 1)
                .param("offset", pagination.offset());
        bindParams(statement, criteria);

        List<InstrumentRow> rows = statement.query(InstrumentRow.class).list();
        boolean hasNext = rows.size() > pagination.size();
        List<Instrument> items = rows.stream()
                .limit(pagination.size())
                .map(JdbcInstrumentSearchAdapter::toDomain)
                .toList();
        return new Page<>(items, pagination.page(), pagination.size(), hasNext);
    }

    private static String where(InstrumentSearchCriteria criteria) {
        StringBuilder clause = new StringBuilder("WHERE i.type <> 'CURRENCY'");
        if (criteria.ticker().isPresent()) {
            clause.append(
                    " AND unaccent(lower(i.ticker)) LIKE unaccent(lower(:tickerPattern)) ESCAPE '\\'");
        }
        if (criteria.name().isPresent()) {
            clause.append(
                    " AND unaccent(lower(i.name)) LIKE unaccent(lower(:namePattern)) ESCAPE '\\'");
        }
        return clause.toString();
    }

    private static String orderBy(InstrumentSearchCriteria criteria) {
        return switch (criteria.sort()) {
            case RELEVANCE -> relevanceOrder(criteria);
            case TICKER -> "ORDER BY i.ticker";
            case NAME -> "ORDER BY unaccent(lower(i.name)), i.ticker";
        };
    }

    private static String relevanceOrder(InstrumentSearchCriteria criteria) {
        if (criteria.ticker().isPresent()) {
            return """
                    ORDER BY
                      CASE
                        WHEN unaccent(lower(i.ticker)) = unaccent(lower(:tickerExact)) THEN 0
                        WHEN unaccent(lower(i.ticker)) LIKE unaccent(lower(:tickerPrefix)) ESCAPE '\\'
                          THEN 1
                        ELSE 2
                      END,
                      i.ticker
                    """;
        }
        if (criteria.name().isPresent()) {
            return """
                    ORDER BY
                      CASE
                        WHEN unaccent(lower(i.name)) = unaccent(lower(:nameExact)) THEN 0
                        WHEN unaccent(lower(i.name)) LIKE unaccent(lower(:namePrefix)) ESCAPE '\\'
                          THEN 1
                        ELSE 2
                      END,
                      i.ticker
                    """;
        }
        return "ORDER BY i.ticker";
    }

    private static void bindParams(StatementSpec statement, InstrumentSearchCriteria criteria) {
        criteria.ticker().ifPresent(text -> {
            bindContains(statement, "tickerPattern", text);
            if (criteria.sort() == InstrumentSort.RELEVANCE) {
                bindRelevance(statement, "ticker", text);
            }
        });
        criteria.name().ifPresent(text -> {
            bindContains(statement, "namePattern", text);
            if (criteria.sort() == InstrumentSort.RELEVANCE && criteria.ticker().isEmpty()) {
                bindRelevance(statement, "name", text);
            }
        });
    }

    private static void bindContains(StatementSpec statement, String param, InstrumentSearchText text) {
        statement.param(param, "%" + escapeLikeMetacharacters(text.value()) + "%");
    }

    private static void bindRelevance(StatementSpec statement, String field, InstrumentSearchText text) {
        String escaped = escapeLikeMetacharacters(text.value());
        statement.param(field + "Exact", text.value());
        statement.param(field + "Prefix", escaped + "%");
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
