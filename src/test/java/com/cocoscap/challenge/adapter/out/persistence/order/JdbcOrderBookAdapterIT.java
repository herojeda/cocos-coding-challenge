package com.cocoscap.challenge.adapter.out.persistence.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cocoscap.challenge.PostgresTestcontainersConfiguration;
import com.cocoscap.challenge.application.order.OrderBookPort;
import com.cocoscap.challenge.application.order.StoredOrder;
import com.cocoscap.challenge.domain.instrument.InstrumentId;
import com.cocoscap.challenge.domain.order.IdempotencyKey;
import com.cocoscap.challenge.domain.order.Order;
import com.cocoscap.challenge.domain.order.OrderDecision;
import com.cocoscap.challenge.domain.order.OrderId;
import com.cocoscap.challenge.domain.order.OrderSide;
import com.cocoscap.challenge.domain.order.OrderStatus;
import com.cocoscap.challenge.domain.order.OrderType;
import com.cocoscap.challenge.domain.order.RejectionReason;
import com.cocoscap.challenge.domain.order.RequestHash;
import com.cocoscap.challenge.domain.user.UserId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
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
class JdbcOrderBookAdapterIT {

    private static final Instant DATETIME = Instant.parse("2023-07-12T15:00:00Z");
    private static final RequestHash HASH = RequestHash.of("a".repeat(64));
    private static final RequestHash OTHER_HASH = RequestHash.of("b".repeat(64));

    @Autowired
    private OrderBookPort orderBookPort;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    @DisplayName("Registers and finds a FILLED order by amount (shape of ORD-08)")
    void registersAndFindsFilledByAmount() {
        UserId userId = UserId.of(insertUser("order-book-filled@test.com"));
        InstrumentId ggal = instrumentId("GGAL");
        IdempotencyKey key = IdempotencyKey.of("ord-08");

        OrderDecision decision = OrderDecision.filled(
                userId,
                ggal,
                OrderSide.BUY,
                OrderType.MARKET,
                11L,
                new BigDecimal("885.80"),
                new BigDecimal("10000.00"));

        OrderId id = orderBookPort.register(decision, key, HASH, DATETIME);
        Optional<StoredOrder> found = orderBookPort.findByUserAndKey(userId, key);

        assertThat(found).isPresent();
        Order order = found.orElseThrow().order();
        assertThat(order.id()).isEqualTo(id);
        assertThat(order.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(order.size()).isEqualTo(11L);
        assertThat(order.amount()).isEqualByComparingTo("10000.00");
        assertThat(order.price()).isEqualByComparingTo("885.80");
        assertThat(order.datetime()).isEqualTo(DATETIME);
        assertThat(found.orElseThrow().requestHash()).isEqualTo(HASH);
    }

    @Test
    @DisplayName("Registers and finds a NEW LIMIT order (shape of ORD-14)")
    void registersAndFindsNewLimit() {
        UserId userId = UserId.of(insertUser("order-book-new@test.com"));
        InstrumentId ggal = instrumentId("GGAL");
        IdempotencyKey key = IdempotencyKey.of("ord-14");

        OrderDecision decision = OrderDecision.accepted(
                userId, ggal, OrderSide.BUY, OrderType.LIMIT, 10L, new BigDecimal("850.00"), null);

        OrderId id = orderBookPort.register(decision, key, HASH, DATETIME);
        Order order = orderBookPort.findByUserAndKey(userId, key).orElseThrow().order();

        assertThat(order.id()).isEqualTo(id);
        assertThat(order.status()).isEqualTo(OrderStatus.NEW);
        assertThat(order.size()).isEqualTo(10L);
        assertThat(order.price()).isEqualByComparingTo("850.00");
        assertThat(order.amount()).isNull();
        assertThat(order.rejectionReason()).isNull();
        assertThat(order.datetime()).isEqualTo(DATETIME);
    }

    @Test
    @DisplayName("Registers and finds a REJECTED order without size or price (shape of ORD-41)")
    void registersAndFindsRejectedWithoutSizeOrPrice() {
        UserId userId = UserId.of(insertUser("order-book-rejected@test.com"));
        InstrumentId pgr = instrumentId("PGR");
        IdempotencyKey key = IdempotencyKey.of("ord-41");

        OrderDecision decision = OrderDecision.rejected(
                userId,
                pgr,
                OrderSide.BUY,
                OrderType.MARKET,
                null,
                null,
                new BigDecimal("10000.00"),
                RejectionReason.NO_MARKET_PRICE);

        OrderId id = orderBookPort.register(decision, key, HASH, DATETIME);
        Order order = orderBookPort.findByUserAndKey(userId, key).orElseThrow().order();

        assertThat(order.id()).isEqualTo(id);
        assertThat(order.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.size()).isNull();
        assertThat(order.price()).isNull();
        assertThat(order.amount()).isEqualByComparingTo("10000.00");
        assertThat(order.rejectionReason()).isEqualTo(RejectionReason.NO_MARKET_PRICE);
        assertThat(order.datetime()).isEqualTo(DATETIME);
    }

    @Test
    @DisplayName("Find by key for another user returns empty")
    void findByKeyForOtherUserIsEmpty() {
        UserId owner = UserId.of(insertUser("order-book-owner@test.com"));
        UserId other = UserId.of(insertUser("order-book-other@test.com"));
        IdempotencyKey key = IdempotencyKey.of("shared-looking");

        orderBookPort.register(
                OrderDecision.accepted(
                        owner,
                        instrumentId("GGAL"),
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        10L,
                        new BigDecimal("850.00"),
                        null),
                key,
                HASH,
                DATETIME);

        assertThat(orderBookPort.findByUserAndKey(other, key)).isEmpty();
    }

    @Test
    @DisplayName("Registering the same key twice for one user violates uniqueness")
    void duplicateKeyForSameUserViolatesUniqueness() {
        UserId userId = UserId.of(insertUser("order-book-dup@test.com"));
        IdempotencyKey key = IdempotencyKey.of("dup-key");
        OrderDecision decision = OrderDecision.accepted(
                userId, instrumentId("GGAL"), OrderSide.BUY, OrderType.LIMIT, 10L, new BigDecimal("850.00"), null);

        orderBookPort.register(decision, key, HASH, DATETIME);

        assertThatThrownBy(() -> orderBookPort.register(decision, key, OTHER_HASH, DATETIME.plusSeconds(1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long insertUser(String email) {
        long userId = jdbcClient
                .sql("INSERT INTO users (email) VALUES (:email) RETURNING id")
                .param("email", email)
                .query(Long.class)
                .single();
        jdbcClient
                .sql(
                        """
                        INSERT INTO accounts (user_id, account_number, cash, reserved_cash, projection_status)
                        VALUES (:userId, :accountNumber, 0, 0, 'PENDING')
                        """)
                .param("userId", userId)
                .param("accountNumber", "OB-" + userId)
                .update();
        return userId;
    }

    private InstrumentId instrumentId(String ticker) {
        long id = jdbcClient
                .sql("SELECT id FROM instruments WHERE ticker = :ticker")
                .param("ticker", ticker)
                .query(Long.class)
                .single();
        return InstrumentId.of(id);
    }
}
