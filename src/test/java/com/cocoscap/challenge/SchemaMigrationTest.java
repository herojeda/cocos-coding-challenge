package com.cocoscap.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
@Transactional
class SchemaMigrationTest {

    private static final String HASH_64 = "a".repeat(64);
    private static final String OTHER_HASH_64 = "b".repeat(64);

    @Autowired
    JdbcClient jdbcClient;

    @Test
    @DisplayName("All six migrations applied successfully")
    void allSixMigrationsApplied() {
        List<String> versions = jdbcClient
                .sql(
                        """
                        SELECT version
                        FROM flyway_schema_history
                        WHERE success
                        ORDER BY installed_rank
                        """)
                .query(String.class)
                .list();

        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6");
    }

    @Test
    @DisplayName("market_data rejects close equal to zero")
    void marketDataRejectsZeroClose() {
        long instrumentId = instrumentId("GGAL");

        assertThatThrownBy(() -> jdbcClient
                        .sql(
                                """
                                INSERT INTO market_data (
                                    instrument_id, date, open, high, low, close, previous_close
                                ) VALUES (
                                    :instrumentId, '2099-01-01', 1, 1, 1, 0, 1
                                )
                                """)
                        .param("instrumentId", instrumentId)
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("orders.datetime is TIMESTAMPTZ; seed order 1 is 2023-07-12T15:11:20Z; ledger index remains")
    void ordersDatetimeIsTimestamptzWithSeedInstantAndIndex() {
        assertThat(columnDataType("orders", "datetime")).isEqualTo("timestamp with time zone");

        OffsetDateTime datetime = jdbcClient
                .sql("SELECT datetime FROM orders WHERE id = 1")
                .query(OffsetDateTime.class)
                .single();
        assertThat(datetime.toInstant()).isEqualTo(Instant.parse("2023-07-12T15:11:20Z"));

        Integer indexCount = jdbcClient
                .sql(
                        """
                        SELECT COUNT(*)
                        FROM pg_indexes
                        WHERE schemaname = 'public'
                          AND tablename = 'orders'
                          AND indexname = 'orders_user_id_datetime_id_idx'
                        """)
                .query(Integer.class)
                .single();
        assertThat(indexCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Tables and columns use snake_case names with BIGINT identifiers")
    void snakeCaseNamesAndBigintIdentifiers() {
        assertThat(columnExists("market_data", "instrument_id")).isTrue();
        assertThat(columnExists("market_data", "previous_close")).isTrue();
        assertThat(columnExists("orders", "instrument_id")).isTrue();
        assertThat(columnExists("orders", "user_id")).isTrue();
        assertThat(columnExists("accounts", "account_number")).isTrue();
        assertThat(tableExists("marketdata")).isFalse();
        assertThat(columnExists("market_data", "instrumentid")).isFalse();
        assertThat(columnExists("market_data", "previousclose")).isFalse();
        assertThat(columnExists("orders", "instrumentid")).isFalse();
        assertThat(columnExists("orders", "userid")).isFalse();
        assertThat(columnExists("users", "accountnumber")).isFalse();
        assertThat(columnExists("users", "account_number")).isFalse();

        assertThat(columnDataType("users", "id")).isEqualTo("bigint");
        assertThat(columnDataType("instruments", "id")).isEqualTo("bigint");
        assertThat(columnDataType("orders", "id")).isEqualTo("bigint");
        assertThat(columnDataType("orders", "user_id")).isEqualTo("bigint");
        assertThat(columnDataType("orders", "instrument_id")).isEqualTo("bigint");
        assertThat(columnDataType("market_data", "id")).isEqualTo("bigint");
        assertThat(columnDataType("market_data", "instrument_id")).isEqualTo("bigint");
        assertThat(columnDataType("accounts", "id")).isEqualTo("bigint");
        assertThat(columnDataType("accounts", "user_id")).isEqualTo("bigint");
        assertThat(columnDataType("positions", "account_id")).isEqualTo("bigint");
        assertThat(columnDataType("positions", "instrument_id")).isEqualTo("bigint");
        assertThat(columnDataType("orders", "size")).isEqualTo("numeric");

        assertThat(sequenceDataType("users_id_seq")).isEqualTo("bigint");
        assertThat(sequenceDataType("instruments_id_seq")).isEqualTo("bigint");
        assertThat(sequenceDataType("orders_id_seq")).isEqualTo("bigint");
        assertThat(sequenceDataType("market_data_id_seq")).isEqualTo("bigint");
        assertThat(sequenceDataType("accounts_id_seq")).isEqualTo("bigint");
        assertThat(sequenceMaxValue("users_id_seq")).isEqualTo(Long.MAX_VALUE);
        assertThat(sequenceMaxValue("instruments_id_seq")).isEqualTo(Long.MAX_VALUE);
        assertThat(sequenceMaxValue("orders_id_seq")).isEqualTo(Long.MAX_VALUE);
        assertThat(sequenceMaxValue("market_data_id_seq")).isEqualTo(Long.MAX_VALUE);
        assertThat(sequenceMaxValue("accounts_id_seq")).isEqualTo(Long.MAX_VALUE);

        String marketDataSequence = jdbcClient
                .sql("SELECT pg_get_serial_sequence('market_data', 'id')")
                .query(String.class)
                .single();
        assertThat(marketDataSequence).endsWith("market_data_id_seq");
    }

    @Test
    @DisplayName("instruments.type normalized to English values")
    void instrumentsTypeNormalized() {
        Map<String, Integer> counts = jdbcClient
                .sql(
                        """
                        SELECT type, COUNT(*) AS count
                        FROM instruments
                        GROUP BY type
                        """)
                .query((rs, rowNum) -> Map.entry(rs.getString("type"), rs.getInt("count")))
                .list()
                .stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        assertThat(counts).containsExactlyInAnyOrderEntriesOf(Map.of("STOCK", 65, "CURRENCY", 1));

        Integer spanishValues = jdbcClient
                .sql(
                        """
                        SELECT COUNT(*)
                        FROM instruments
                        WHERE type IN ('ACCIONES', 'MONEDA')
                        """)
                .query(Integer.class)
                .single();
        assertThat(spanishValues).isZero();
    }

    @Test
    @DisplayName("Seed REJECTED order has no rejection reason")
    void seedRejectedOrderHasNoRejectionReason() {
        Integer rejectedWithoutReason = jdbcClient
                .sql(
                        """
                        SELECT COUNT(*)
                        FROM orders
                        WHERE status = 'REJECTED' AND rejection_reason IS NULL
                        """)
                .query(Integer.class)
                .single();
        Integer rejectedTotal = jdbcClient
                .sql("SELECT COUNT(*) FROM orders WHERE status = 'REJECTED'")
                .query(Integer.class)
                .single();

        assertThat(rejectedTotal).isEqualTo(1);
        assertThat(rejectedWithoutReason).isEqualTo(1);
    }

    @Test
    @DisplayName("One PENDING account per seed user; positions empty; users without account_number")
    void pendingAccountsCopiedFromUsers() {
        List<Map<String, Object>> accounts = jdbcClient
                .sql(
                        """
                        SELECT account_number, cash, reserved_cash, projection_status
                        FROM accounts
                        ORDER BY account_number
                        """)
                .query()
                .listOfRows();

        assertThat(accounts).hasSize(4);
        assertThat(accounts)
                .extracting(row -> row.get("account_number"))
                .containsExactly("10001", "10002", "10003", "10004");
        assertThat(accounts).allSatisfy(row -> {
            assertThat(row.get("projection_status")).isEqualTo("PENDING");
            assertThat((BigDecimal) row.get("cash")).isEqualByComparingTo("0");
            assertThat((BigDecimal) row.get("reserved_cash")).isEqualByComparingTo("0");
        });

        assertThat(columnExists("users", "account_number")).isFalse();

        Integer positions = jdbcClient
                .sql("SELECT COUNT(*) FROM positions")
                .query(Integer.class)
                .single();
        assertThat(positions).isZero();
    }

    @Test
    @DisplayName("unaccent extension strips accents")
    void unaccentExtensionWorks() {
        String result = jdbcClient
                .sql("SELECT unaccent('Río')")
                .query(String.class)
                .single();
        assertThat(result).isEqualTo("Rio");
    }

    @Test
    @DisplayName("Fractional BUY size is rejected")
    void fractionalBuySizeIsRejected() {
        long instrumentId = instrumentId("GGAL");
        long userId = userId("emiliano@test.com");

        assertThatThrownBy(() -> insertOrder(instrumentId, userId, "1.50", "BUY", "MARKET", "FILLED", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("CASH_IN with cents is accepted")
    void cashInWithCentsIsAccepted() {
        long instrumentId = instrumentId("ARS");
        long userId = userId("emiliano@test.com");

        insertOrder(instrumentId, userId, "10.50", "CASH_IN", "MARKET", "FILLED", null, null);

        BigDecimal size = jdbcClient
                .sql(
                        """
                        SELECT size
                        FROM orders
                        WHERE user_id = :userId AND side = 'CASH_IN' AND size = 10.50
                        """)
                .param("userId", userId)
                .query(BigDecimal.class)
                .single();
        assertThat(size).isEqualByComparingTo("10.50");
    }

    @Test
    @DisplayName("Rejection reason on FILLED order is rejected")
    void rejectionReasonOnFilledOrderIsRejected() {
        long instrumentId = instrumentId("GGAL");
        long userId = userId("emiliano@test.com");

        assertThatThrownBy(() -> insertOrder(
                        instrumentId,
                        userId,
                        "1",
                        "BUY",
                        "MARKET",
                        "FILLED",
                        "INSUFFICIENT_CASH",
                        null,
                        null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Duplicate ticker is rejected")
    void duplicateTickerIsRejected() {
        assertThatThrownBy(() -> jdbcClient
                        .sql(
                                """
                                INSERT INTO instruments (ticker, name, type)
                                VALUES ('GGAL', 'Duplicate Galicia', 'STOCK')
                                """)
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Email differing only by case is rejected")
    void emailDifferingOnlyByCaseIsRejected() {
        assertThatThrownBy(() -> jdbcClient
                        .sql(
                                """
                                INSERT INTO users (email)
                                VALUES ('Emiliano@test.com')
                                """)
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Same idempotency key is rejected for the same user")
    void sameIdempotencyKeyRejectedForSameUser() {
        long instrumentId = instrumentId("GGAL");
        long userId = userId("emiliano@test.com");

        insertOrder(instrumentId, userId, "1", "BUY", "MARKET", "FILLED", "key-1", HASH_64);

        assertThatThrownBy(() -> insertOrder(instrumentId, userId, "2", "BUY", "MARKET", "FILLED", "key-1", OTHER_HASH_64))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Same idempotency key is accepted for a different user")
    void sameIdempotencyKeyAcceptedForDifferentUser() {
        long instrumentId = instrumentId("GGAL");
        long firstUserId = userId("emiliano@test.com");
        long secondUserId = userId("jose@test.com");

        insertOrder(instrumentId, firstUserId, "1", "BUY", "MARKET", "FILLED", "shared-key", HASH_64);
        insertOrder(instrumentId, secondUserId, "1", "BUY", "MARKET", "FILLED", "shared-key", HASH_64);

        Integer count = jdbcClient
                .sql(
                        """
                        SELECT COUNT(*)
                        FROM orders
                        WHERE idempotency_key = 'shared-key'
                        """)
                .query(Integer.class)
                .single();
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("Idempotency key without request_hash is rejected")
    void idempotencyKeyWithoutRequestHashIsRejected() {
        long instrumentId = instrumentId("GGAL");
        long userId = userId("emiliano@test.com");

        assertThatThrownBy(() -> insertOrder(instrumentId, userId, "1", "BUY", "MARKET", "FILLED", "orphan-key", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertOrder(
            long instrumentId,
            long userId,
            String size,
            String side,
            String type,
            String status,
            String idempotencyKey,
            String requestHash) {
        insertOrder(instrumentId, userId, size, side, type, status, null, idempotencyKey, requestHash);
    }

    private void insertOrder(
            long instrumentId,
            long userId,
            String size,
            String side,
            String type,
            String status,
            String rejectionReason,
            String idempotencyKey,
            String requestHash) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO orders (
                            instrument_id, user_id, size, price, type, side, status, datetime,
                            rejection_reason, idempotency_key, request_hash
                        ) VALUES (
                            :instrumentId, :userId, :size, 100.00, :type, :side, :status, NOW(),
                            :rejectionReason, :idempotencyKey, :requestHash
                        )
                        """)
                .param("instrumentId", instrumentId)
                .param("userId", userId)
                .param("size", new BigDecimal(size))
                .param("type", type)
                .param("side", side)
                .param("status", status)
                .param("rejectionReason", rejectionReason)
                .param("idempotencyKey", idempotencyKey)
                .param("requestHash", requestHash)
                .update();
    }

    private long instrumentId(String ticker) {
        return jdbcClient
                .sql("SELECT id FROM instruments WHERE ticker = :ticker")
                .param("ticker", ticker)
                .query(Long.class)
                .single();
    }

    private long userId(String email) {
        return jdbcClient
                .sql("SELECT id FROM users WHERE email = :email")
                .param("email", email)
                .query(Long.class)
                .single();
    }

    private boolean tableExists(String table) {
        Integer count = jdbcClient
                .sql(
                        """
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = 'public' AND table_name = :table
                        """)
                .param("table", table)
                .query(Integer.class)
                .single();
        return count == 1;
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcClient
                .sql(
                        """
                        SELECT COUNT(*)
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = :table
                          AND column_name = :column
                        """)
                .param("table", table)
                .param("column", column)
                .query(Integer.class)
                .single();
        return count == 1;
    }

    private String columnDataType(String table, String column) {
        return jdbcClient
                .sql(
                        """
                        SELECT data_type
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = :table
                          AND column_name = :column
                        """)
                .param("table", table)
                .param("column", column)
                .query(String.class)
                .single();
    }

    private String sequenceDataType(String sequence) {
        return jdbcClient
                .sql(
                        """
                        SELECT data_type
                        FROM information_schema.sequences
                        WHERE sequence_schema = 'public' AND sequence_name = :sequence
                        """)
                .param("sequence", sequence)
                .query(String.class)
                .single();
    }

    private long sequenceMaxValue(String sequence) {
        return jdbcClient
                .sql(
                        """
                        SELECT maximum_value
                        FROM information_schema.sequences
                        WHERE sequence_schema = 'public' AND sequence_name = :sequence
                        """)
                .param("sequence", sequence)
                .query(Long.class)
                .single();
    }
}
