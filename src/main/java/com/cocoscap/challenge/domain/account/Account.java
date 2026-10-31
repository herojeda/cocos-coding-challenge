package com.cocoscap.challenge.domain.account;

import com.cocoscap.challenge.domain.instrument.InstrumentId;
import com.cocoscap.challenge.domain.order.OrderType;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Materialized cash, reservations and positions. Transitions match ADR-005 except cost basis /
 * realized PnL (next PR). They do not validate available cash or quantity.
 *
 * <p>Equality is by {@link AccountId} only.
 */
public final class Account {

    private final AccountId id;
    private BigDecimal cash = BigDecimal.ZERO;
    private BigDecimal reservedCash = BigDecimal.ZERO;
    private final Map<InstrumentId, Position> positions = new LinkedHashMap<>();

    private Account(AccountId id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public static Account empty(AccountId id) {
        return new Account(id);
    }

    public AccountId id() {
        return id;
    }

    public BigDecimal cash() {
        return cash;
    }

    public BigDecimal reservedCash() {
        return reservedCash;
    }

    public BigDecimal availableCash() {
        return cash.subtract(reservedCash);
    }

    public Optional<Position> position(InstrumentId instrumentId) {
        return Optional.ofNullable(positions.get(instrumentId));
    }

    public long availableQuantity(InstrumentId instrumentId) {
        Position position = positions.get(instrumentId);
        return position == null ? 0L : position.availableQuantity();
    }

    public void applyBuyFill(InstrumentId instrumentId, long quantity, BigDecimal price) {
        BigDecimal notional = notional(quantity, price);
        cash = cash.subtract(notional);
        positionFor(instrumentId).applyBuy(quantity);
    }

    public void applySellFill(InstrumentId instrumentId, long quantity, BigDecimal price) {
        BigDecimal notional = notional(quantity, price);
        cash = cash.add(notional);
        positionFor(instrumentId).applySell(quantity);
    }

    public void reserveCash(BigDecimal amount) {
        requirePositive(amount, "amount");
        reservedCash = reservedCash.add(amount);
    }

    public void releaseCash(BigDecimal amount) {
        requirePositive(amount, "amount");
        if (amount.compareTo(reservedCash) > 0) {
            throw new IllegalStateException("reserved cash would become negative");
        }
        reservedCash = reservedCash.subtract(amount);
    }

    public void reserveShares(InstrumentId instrumentId, long quantity) {
        positionFor(instrumentId).reserve(quantity);
    }

    public void releaseShares(InstrumentId instrumentId, long quantity) {
        Position position = positions.get(instrumentId);
        if (position == null) {
            throw new IllegalStateException("reserved quantity would become negative");
        }
        position.release(quantity);
    }

    public void cashIn(BigDecimal amount) {
        requirePositive(amount, "amount");
        cash = cash.add(amount);
    }

    public void cashOut(BigDecimal amount) {
        requirePositive(amount, "amount");
        cash = cash.subtract(amount);
    }

    /**
     * Rebuilds an existing account from an already-ordered ledger stream. Does not sort or buffer
     * the history; fails if entries are not strictly increasing by {@code (datetime, id)}.
     */
    public static Account rebuildFrom(AccountId accountId, Stream<LedgerEntry> entries) {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(entries, "entries");
        Account account = empty(accountId);
        LedgerEntry[] previous = {null};
        entries.forEach(entry -> {
            Objects.requireNonNull(entry, "entry");
            if (previous[0] != null && compareLedgerOrder(entry, previous[0]) <= 0) {
                throw new IllegalStateException(
                        "ledger entries must be strictly ascending by datetime then id");
            }
            account.apply(entry);
            previous[0] = entry;
        });
        return account;
    }

    private void apply(LedgerEntry entry) {
        switch (entry.status()) {
            case FILLED -> applyFilled(entry);
            case NEW -> applyNew(entry);
            case REJECTED, CANCELLED -> {
                // Current status does not move cash, holdings or reservations.
            }
        }
    }

    private void applyFilled(LedgerEntry entry) {
        switch (entry.side()) {
            case BUY -> applyBuyFill(entry.instrumentId(), shareQuantity(entry), requirePrice(entry));
            case SELL -> applySellFill(entry.instrumentId(), shareQuantity(entry), requirePrice(entry));
            case CASH_IN -> cashIn(entry.size());
            case CASH_OUT -> cashOut(entry.size());
        }
    }

    private void applyNew(LedgerEntry entry) {
        if (entry.type() != OrderType.LIMIT) {
            throw new IllegalStateException(
                    "NEW orders must be LIMIT; got " + entry.type() + " " + entry.side());
        }
        switch (entry.side()) {
            case BUY -> reserveCash(entry.size().multiply(requirePrice(entry)));
            case SELL -> reserveShares(entry.instrumentId(), shareQuantity(entry));
            case CASH_IN, CASH_OUT -> throw new IllegalStateException(
                    "NEW cash transfers are not valid ledger entries");
        }
    }

    private Position positionFor(InstrumentId instrumentId) {
        return positions.computeIfAbsent(instrumentId, Position::new);
    }

    private static BigDecimal requirePrice(LedgerEntry entry) {
        if (entry.price() == null) {
            throw new IllegalStateException("price is required for " + entry.status() + " " + entry.side());
        }
        return entry.price();
    }

    private static long shareQuantity(LedgerEntry entry) {
        try {
            return entry.size().longValueExact();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("share quantity must be a whole number", ex);
        }
    }

    private static int compareLedgerOrder(LedgerEntry left, LedgerEntry right) {
        int byTime = left.datetime().compareTo(right.datetime());
        if (byTime != 0) {
            return byTime;
        }
        return Long.compare(left.id().value(), right.id().value());
    }

    private static BigDecimal notional(long quantity, BigDecimal price) {
        requirePositive(quantity);
        requirePositive(price, "price");
        return BigDecimal.valueOf(quantity).multiply(price);
    }

    private static void requirePositive(long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }

    private static void requirePositive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Account account && id.equals(account.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
