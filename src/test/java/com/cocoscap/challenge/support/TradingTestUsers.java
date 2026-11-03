package com.cocoscap.challenge.support;

import com.cocoscap.challenge.domain.account.Account;
import com.cocoscap.challenge.domain.account.AccountId;
import com.cocoscap.challenge.domain.account.LedgerEntry;
import com.cocoscap.challenge.domain.costbasis.CostBasisCalculator;
import com.cocoscap.challenge.domain.costbasis.WeightedAverageCostBasisCalculator;
import com.cocoscap.challenge.domain.user.UserId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Shared HTTP/IT fixture: creates a user with a PENDING account and a CASH_IN so the first write
 * rebuilds available cash along the production path.
 */
public final class TradingTestUsers {

    private static final CostBasisCalculator COST_BASIS = new WeightedAverageCostBasisCalculator();

    private final JdbcClient jdbcClient;

    public TradingTestUsers(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient");
    }

    public TestUser createWithCash(BigDecimal cashInAmount) {
        Objects.requireNonNull(cashInAmount, "cashInAmount");
        String email = "trading-" + UUID.randomUUID() + "@test.com";
        long userId = jdbcClient
                .sql("INSERT INTO users (email) VALUES (:email) RETURNING id")
                .param("email", email)
                .query(Long.class)
                .single();
        long accountId = jdbcClient
                .sql(
                        """
                        INSERT INTO accounts (user_id, account_number, cash, reserved_cash, projection_status)
                        VALUES (:userId, :accountNumber, 0, 0, 'PENDING')
                        RETURNING id
                        """)
                .param("userId", userId)
                .param("accountNumber", "T-" + userId)
                .query(Long.class)
                .single();
        long arsId = instrumentId("ARS");
        Instant datetime = Instant.parse("2023-07-12T12:00:00Z");
        jdbcClient
                .sql(
                        """
                        INSERT INTO orders (
                            instrument_id, user_id, size, price, type, side, status, datetime
                        ) VALUES (
                            :instrumentId, :userId, :size, 1.00, 'MARKET', 'CASH_IN', 'FILLED', :datetime
                        )
                        """)
                .param("instrumentId", arsId)
                .param("userId", userId)
                .param("size", cashInAmount)
                .param("datetime", OffsetDateTime.ofInstant(datetime, ZoneOffset.UTC))
                .update();
        return new TestUser(userId, accountId);
    }

    public long instrumentId(String ticker) {
        return jdbcClient
                .sql("SELECT id FROM instruments WHERE ticker = :ticker")
                .param("ticker", ticker)
                .query(Long.class)
                .single();
    }

    public long countOrders(long userId) {
        return jdbcClient
                .sql(
                        """
                        SELECT COUNT(*) FROM orders
                        WHERE user_id = :userId AND side IN ('BUY', 'SELL')
                        """)
                .param("userId", userId)
                .query(Long.class)
                .single();
    }

    public BigDecimal availableCash(long userId) {
        return jdbcClient
                .sql("SELECT cash - reserved_cash FROM accounts WHERE user_id = :userId")
                .param("userId", userId)
                .query(BigDecimal.class)
                .single();
    }

    public Account rebuildFromLedger(TestUser user, Stream<LedgerEntry> entries) {
        return Account.rebuildFrom(AccountId.of(user.accountId()), entries, COST_BASIS);
    }

    public UserId userId(TestUser user) {
        return UserId.of(user.userId());
    }

    public record TestUser(long userId, long accountId) {}
}
