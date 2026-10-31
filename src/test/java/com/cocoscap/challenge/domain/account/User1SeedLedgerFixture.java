package com.cocoscap.challenge.domain.account;

import com.cocoscap.challenge.domain.instrument.InstrumentId;
import com.cocoscap.challenge.domain.order.OrderId;
import com.cocoscap.challenge.domain.order.OrderSide;
import com.cocoscap.challenge.domain.order.OrderStatus;
import com.cocoscap.challenge.domain.order.OrderType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * In-memory copy of user 1's eleven orders from {@code V1__cocos_seed.sql}.
 * Seed clock times are interpreted as America/Argentina/Buenos_Aires (S14).
 */
public final class User1SeedLedgerFixture {

    public static final InstrumentId BMA = InstrumentId.of(31L);
    public static final InstrumentId LOMA = InstrumentId.of(45L);
    public static final InstrumentId PAMP = InstrumentId.of(47L);
    public static final InstrumentId METR = InstrumentId.of(54L);
    public static final InstrumentId ARS = InstrumentId.of(66L);

    private static final ZoneId SEED_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    private User1SeedLedgerFixture() {}

    public static List<LedgerEntry> entries() {
        return List.of(
                entry(1, ARS, OrderSide.CASH_IN, OrderType.MARKET, OrderStatus.FILLED, "1000000", "1", "2023-07-12T12:11:20"),
                entry(2, PAMP, OrderSide.BUY, OrderType.MARKET, OrderStatus.FILLED, "50", "930", "2023-07-12T12:31:20"),
                entry(3, PAMP, OrderSide.BUY, OrderType.LIMIT, OrderStatus.CANCELLED, "50", "920", "2023-07-12T14:21:20"),
                entry(4, PAMP, OrderSide.SELL, OrderType.MARKET, OrderStatus.FILLED, "10", "940", "2023-07-12T14:51:20"),
                entry(5, LOMA, OrderSide.BUY, OrderType.LIMIT, OrderStatus.NEW, "50", "710", "2023-07-12T15:14:20"),
                entry(6, PAMP, OrderSide.SELL, OrderType.MARKET, OrderStatus.REJECTED, "100", "950", "2023-07-12T16:11:20"),
                entry(7, BMA, OrderSide.BUY, OrderType.LIMIT, OrderStatus.NEW, "60", "1500", "2023-07-13T11:13:20"),
                entry(8, ARS, OrderSide.CASH_OUT, OrderType.MARKET, OrderStatus.FILLED, "100000", "1", "2023-07-13T12:31:20"),
                entry(9, BMA, OrderSide.BUY, OrderType.LIMIT, OrderStatus.FILLED, "20", "1540", "2023-07-13T12:51:20"),
                entry(10, METR, OrderSide.BUY, OrderType.MARKET, OrderStatus.FILLED, "500", "250", "2023-07-13T14:11:20"),
                entry(11, BMA, OrderSide.SELL, OrderType.MARKET, OrderStatus.FILLED, "30", "1530", "2023-07-13T15:13:20"));
    }

    private static LedgerEntry entry(
            long id,
            InstrumentId instrumentId,
            OrderSide side,
            OrderType type,
            OrderStatus status,
            String size,
            String price,
            String localDateTime) {
        Instant datetime = LocalDateTime.parse(localDateTime).atZone(SEED_ZONE).toInstant();
        return new LedgerEntry(
                OrderId.of(id),
                instrumentId,
                side,
                type,
                status,
                new BigDecimal(size),
                new BigDecimal(price),
                datetime);
    }
}
