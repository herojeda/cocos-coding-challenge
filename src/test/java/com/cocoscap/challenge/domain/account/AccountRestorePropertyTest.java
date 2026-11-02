package com.cocoscap.challenge.domain.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocoscap.challenge.domain.costbasis.CostBasisCalculator;
import com.cocoscap.challenge.domain.costbasis.WeightedAverageCostBasisCalculator;
import com.cocoscap.challenge.domain.instrument.InstrumentId;
import com.cocoscap.challenge.domain.order.OrderId;
import com.cocoscap.challenge.domain.order.OrderSide;
import com.cocoscap.challenge.domain.order.OrderStatus;
import com.cocoscap.challenge.domain.order.OrderType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Property: restoring a mid-ledger snapshot and applying the suffix with public transitions yields
 * the same photo as {@link Account#rebuildFrom} over the full ledger (ADR-005 reconciliation).
 */
class AccountRestorePropertyTest {

    private static final long SEED = 20251101L;
    private static final int SEQUENCES = 200;
    private static final int MAX_GENERATED = 40;
    private static final AccountId ACCOUNT = AccountId.of(1L);
    private static final CostBasisCalculator COST_BASIS = new WeightedAverageCostBasisCalculator();
    private static final InstrumentId ARS = User1SeedLedgerFixture.ARS;
    private static final InstrumentId[] INSTRUMENTS = {
        InstrumentId.of(101L), InstrumentId.of(102L), InstrumentId.of(103L)
    };
    /** Prices chosen so weighted averages can produce repeating decimals (scales 6 and 2). */
    private static final BigDecimal[] PRICES = {
        new BigDecimal("100.00"),
        new BigDecimal("133.33"),
        new BigDecimal("66.67"),
        new BigDecimal("150.00"),
        new BigDecimal("87.50")
    };

    @Test
    @DisplayName("Property · restored incremental path equals rebuild from the ledger")
    void restoredIncrementalPathEqualsRebuildFromLedger() {
        Random random = new Random(SEED);
        for (int sequence = 0; sequence < SEQUENCES; sequence++) {
            List<LedgerEntry> entries = generateSequence(random, sequence % 2 == 0);
            int cut = random.nextInt(entries.size() + 1);
            AccountSnapshot expected =
                    Account.rebuildFrom(ACCOUNT, entries.stream(), COST_BASIS).snapshot();
            Account restored = Account.restore(
                    Account.rebuildFrom(ACCOUNT, entries.subList(0, cut).stream(), COST_BASIS).snapshot(),
                    COST_BASIS);
            for (int i = cut; i < entries.size(); i++) {
                applyIncremental(restored, entries.get(i));
            }
            assertThat(restored.snapshot())
                    .as("seed=%d sequence=%d cut=%d", SEED, sequence, cut)
                    .isEqualTo(expected);
        }
    }

    private static List<LedgerEntry> generateSequence(Random random, boolean withSeedPrefix) {
        List<LedgerEntry> entries = new ArrayList<>();
        long nextId = 1L;
        Instant nextTime = Instant.parse("2024-01-01T00:00:00Z");
        if (withSeedPrefix) {
            entries.addAll(User1SeedLedgerFixture.entries());
            LedgerEntry last = entries.get(entries.size() - 1);
            nextId = last.id().value() + 1;
            nextTime = last.datetime().plusSeconds(60);
        }
        Account shadow = Account.rebuildFrom(ACCOUNT, entries.stream(), COST_BASIS);
        int toGenerate = 1 + random.nextInt(MAX_GENERATED);
        for (int i = 0; i < toGenerate; i++) {
            LedgerEntry entry = nextEntry(random, shadow, nextId, nextTime);
            entries.add(entry);
            applyShadow(shadow, entry);
            nextId++;
            nextTime = nextTime.plusSeconds(1 + random.nextInt(120));
        }
        return entries;
    }

    private static LedgerEntry nextEntry(Random random, Account shadow, long id, Instant at) {
        List<Kind> kinds = new ArrayList<>();
        kinds.add(Kind.CASH_IN);
        kinds.add(Kind.REJECTED);
        if (shadow.availableCash().signum() > 0) {
            kinds.add(Kind.CASH_OUT);
        }
        if (shadow.availableCash().compareTo(minPrice()) >= 0) {
            kinds.add(Kind.BUY_FILLED);
            kinds.add(Kind.BUY_LIMIT_NEW);
            kinds.add(Kind.BUY_LIMIT_CANCELLED);
        }
        for (InstrumentId instrument : INSTRUMENTS) {
            if (shadow.availableQuantity(instrument) > 0) {
                kinds.add(Kind.SELL_FILLED);
                kinds.add(Kind.SELL_LIMIT_NEW);
                kinds.add(Kind.SELL_LIMIT_CANCELLED);
                break;
            }
        }
        return switch (kinds.get(random.nextInt(kinds.size()))) {
            case CASH_IN -> cash(id, OrderSide.CASH_IN, cashAmount(random, new BigDecimal("100000.00")), at);
            case CASH_OUT -> cash(id, OrderSide.CASH_OUT, cashAmount(random, shadow.availableCash()), at);
            case BUY_FILLED -> buyOrSellFilled(random, shadow, id, at, OrderSide.BUY);
            case SELL_FILLED -> buyOrSellFilled(random, shadow, id, at, OrderSide.SELL);
            case BUY_LIMIT_NEW -> limit(random, shadow, id, at, OrderSide.BUY, OrderStatus.NEW);
            case SELL_LIMIT_NEW -> limit(random, shadow, id, at, OrderSide.SELL, OrderStatus.NEW);
            case BUY_LIMIT_CANCELLED -> limit(random, shadow, id, at, OrderSide.BUY, OrderStatus.CANCELLED);
            case SELL_LIMIT_CANCELLED -> limit(random, shadow, id, at, OrderSide.SELL, OrderStatus.CANCELLED);
            case REJECTED -> rejected(random, id, at);
        };
    }

    private static LedgerEntry buyOrSellFilled(
            Random random, Account shadow, long id, Instant at, OrderSide side) {
        InstrumentId instrument = pickInstrument(random, shadow, side);
        BigDecimal price = priceFor(random, shadow, side);
        long quantity = shareQuantity(random, shadow, instrument, side, price);
        return entry(id, instrument, side, OrderType.MARKET, OrderStatus.FILLED, quantity, price, at);
    }

    private static LedgerEntry limit(
            Random random, Account shadow, long id, Instant at, OrderSide side, OrderStatus status) {
        InstrumentId instrument = pickInstrument(random, shadow, side);
        BigDecimal price = priceFor(random, shadow, side);
        long quantity = shareQuantity(random, shadow, instrument, side, price);
        return entry(id, instrument, side, OrderType.LIMIT, status, quantity, price, at);
    }

    private static BigDecimal priceFor(Random random, Account shadow, OrderSide side) {
        if (side != OrderSide.BUY) {
            return PRICES[random.nextInt(PRICES.length)];
        }
        List<BigDecimal> affordable = new ArrayList<>();
        for (BigDecimal price : PRICES) {
            if (shadow.availableCash().compareTo(price) >= 0) {
                affordable.add(price);
            }
        }
        return affordable.get(random.nextInt(affordable.size()));
    }

    private static LedgerEntry rejected(Random random, long id, Instant at) {
        InstrumentId instrument = INSTRUMENTS[random.nextInt(INSTRUMENTS.length)];
        return entry(
                id,
                instrument,
                random.nextBoolean() ? OrderSide.BUY : OrderSide.SELL,
                OrderType.MARKET,
                OrderStatus.REJECTED,
                1 + random.nextInt(10),
                PRICES[random.nextInt(PRICES.length)],
                at);
    }

    private static InstrumentId pickInstrument(Random random, Account shadow, OrderSide side) {
        if (side == OrderSide.BUY) {
            return INSTRUMENTS[random.nextInt(INSTRUMENTS.length)];
        }
        List<InstrumentId> held = new ArrayList<>();
        for (InstrumentId instrument : INSTRUMENTS) {
            if (shadow.availableQuantity(instrument) > 0) {
                held.add(instrument);
            }
        }
        return held.get(random.nextInt(held.size()));
    }

    private static long shareQuantity(
            Random random, Account shadow, InstrumentId instrument, OrderSide side, BigDecimal price) {
        if (side == OrderSide.BUY) {
            long maxByCash = shadow.availableCash().divide(price, 0, RoundingMode.DOWN).longValue();
            if (maxByCash < 1) {
                throw new IllegalStateException("buy selected without affordable cash");
            }
            return 1 + random.nextInt((int) Math.min(maxByCash, 20L));
        }
        long available = shadow.availableQuantity(instrument);
        return 1 + random.nextInt((int) Math.min(available, 20L));
    }

    private static BigDecimal minPrice() {
        BigDecimal min = PRICES[0];
        for (BigDecimal price : PRICES) {
            if (price.compareTo(min) < 0) {
                min = price;
            }
        }
        return min;
    }

    private static BigDecimal cashAmount(Random random, BigDecimal max) {
        BigDecimal capped = max.min(new BigDecimal("50000.00"));
        long cents = capped.movePointRight(2).longValue();
        long pick = 1 + (cents <= 1 ? 0 : random.nextLong(cents));
        return BigDecimal.valueOf(pick, 2);
    }

    private static void applyShadow(Account shadow, LedgerEntry entry) {
        switch (entry.status()) {
            case FILLED, NEW -> applyIncremental(shadow, entry);
            case REJECTED, CANCELLED -> {
                // Matches rebuildFrom: final CANCELLED / REJECTED do not move state.
            }
        }
    }

    private static void applyIncremental(Account account, LedgerEntry entry) {
        switch (entry.status()) {
            case FILLED -> {
                switch (entry.side()) {
                    case BUY -> account.applyBuyFill(entry.instrumentId(), qty(entry), entry.price());
                    case SELL -> account.applySellFill(entry.instrumentId(), qty(entry), entry.price());
                    case CASH_IN -> account.cashIn(entry.size());
                    case CASH_OUT -> account.cashOut(entry.size());
                }
            }
            case NEW -> {
                if (entry.side() == OrderSide.BUY) {
                    account.reserveCash(entry.size().multiply(entry.price()));
                } else {
                    account.reserveShares(entry.instrumentId(), qty(entry));
                }
            }
            case CANCELLED -> {
                if (entry.side() == OrderSide.BUY) {
                    BigDecimal amount = entry.size().multiply(entry.price());
                    account.reserveCash(amount);
                    account.releaseCash(amount);
                } else {
                    long quantity = qty(entry);
                    account.reserveShares(entry.instrumentId(), quantity);
                    account.releaseShares(entry.instrumentId(), quantity);
                }
            }
            case REJECTED -> {
                // no-op
            }
        }
    }

    private static long qty(LedgerEntry entry) {
        return entry.size().longValueExact();
    }

    private static LedgerEntry cash(long id, OrderSide side, BigDecimal amount, Instant at) {
        return entry(id, ARS, side, OrderType.MARKET, OrderStatus.FILLED, amount, BigDecimal.ONE, at);
    }

    private static LedgerEntry entry(
            long id,
            InstrumentId instrumentId,
            OrderSide side,
            OrderType type,
            OrderStatus status,
            long size,
            BigDecimal price,
            Instant at) {
        return entry(id, instrumentId, side, type, status, BigDecimal.valueOf(size), price, at);
    }

    private static LedgerEntry entry(
            long id,
            InstrumentId instrumentId,
            OrderSide side,
            OrderType type,
            OrderStatus status,
            BigDecimal size,
            BigDecimal price,
            Instant at) {
        return new LedgerEntry(OrderId.of(id), instrumentId, side, type, status, size, price, at);
    }

    private enum Kind {
        CASH_IN,
        CASH_OUT,
        BUY_FILLED,
        SELL_FILLED,
        BUY_LIMIT_NEW,
        SELL_LIMIT_NEW,
        BUY_LIMIT_CANCELLED,
        SELL_LIMIT_CANCELLED,
        REJECTED
    }
}
