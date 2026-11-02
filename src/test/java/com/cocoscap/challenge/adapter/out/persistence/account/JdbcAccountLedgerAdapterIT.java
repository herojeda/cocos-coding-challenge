package com.cocoscap.challenge.adapter.out.persistence.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocoscap.challenge.PostgresTestcontainersConfiguration;
import com.cocoscap.challenge.application.account.AccountLedgerPort;
import com.cocoscap.challenge.domain.account.Account;
import com.cocoscap.challenge.domain.account.AccountId;
import com.cocoscap.challenge.domain.account.LedgerEntry;
import com.cocoscap.challenge.domain.account.User1SeedLedgerFixture;
import com.cocoscap.challenge.domain.costbasis.CostBasisCalculator;
import com.cocoscap.challenge.domain.costbasis.WeightedAverageCostBasisCalculator;
import com.cocoscap.challenge.domain.order.OrderId;
import com.cocoscap.challenge.domain.user.UserId;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;
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
class JdbcAccountLedgerAdapterIT {

    private static final CostBasisCalculator COST_BASIS = new WeightedAverageCostBasisCalculator();

    @Autowired
    private AccountLedgerPort accountLedgerPort;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    @DisplayName("ACB-11 · Disponible del usuario de la seed")
    void acb11SeedAvailableCashFromPersistedLedger() {
        UserId userId = UserId.of(1L);
        AccountId accountId = AccountId.of(accountIdForUser(1L));

        Account fromDb;
        try (Stream<LedgerEntry> stream = accountLedgerPort.streamByUserId(userId)) {
            fromDb = Account.rebuildFrom(accountId, stream, COST_BASIS);
        }

        assertThat(fromDb.cash()).isEqualByComparingTo("753000");
        assertThat(fromDb.availableCash()).isEqualByComparingTo("627500");

        Account fromFixture = Account.rebuildFrom(
                accountId, User1SeedLedgerFixture.entries().stream(), COST_BASIS);
        assertThat(fromDb.state()).isEqualTo(fromFixture.state());
    }

    @Test
    @DisplayName("Ledger stream is ordered by datetime then id")
    void ledgerStreamOrderedByDatetimeThenId() {
        long userId = insertUser("ledger-order@test.com");
        Instant earlier = Instant.parse("2023-07-12T08:00:00Z");
        Instant later = Instant.parse("2023-07-12T10:00:00Z");

        long idAtLater = insertOrder(userId, later);
        long idAtEarlier = insertOrder(userId, earlier);
        long idAtLaterAgain = insertOrder(userId, later);

        List<Long> ids;
        try (Stream<LedgerEntry> stream = accountLedgerPort.streamByUserId(UserId.of(userId))) {
            ids = stream.map(LedgerEntry::id).map(OrderId::value).toList();
        }

        assertThat(ids).containsExactly(idAtEarlier, idAtLater, idAtLaterAgain);
    }

    private long accountIdForUser(long userId) {
        return jdbcClient
                .sql("SELECT id FROM accounts WHERE user_id = :userId")
                .param("userId", userId)
                .query(Long.class)
                .single();
    }

    private long insertUser(String email) {
        return jdbcClient
                .sql("INSERT INTO users (email) VALUES (:email) RETURNING id")
                .param("email", email)
                .query(Long.class)
                .single();
    }

    private long insertOrder(long userId, Instant datetime) {
        long arsId = jdbcClient
                .sql("SELECT id FROM instruments WHERE ticker = 'ARS'")
                .query(Long.class)
                .single();
        return jdbcClient
                .sql(
                        """
                        INSERT INTO orders (
                            instrument_id, user_id, size, price, type, side, status, datetime
                        ) VALUES (
                            :instrumentId, :userId, 1, 1.00, 'MARKET', 'CASH_IN', 'FILLED', :datetime
                        )
                        RETURNING id
                        """)
                .param("instrumentId", arsId)
                .param("userId", userId)
                .param("datetime", OffsetDateTime.ofInstant(datetime, ZoneOffset.UTC))
                .query(Long.class)
                .single();
    }
}
