package com.cocoscap.challenge.domain.account;

import com.cocoscap.challenge.domain.order.OrderType;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Materialized cash, reservations and positions. Transitions match ADR-005 except cost basis /
 * realized PnL (next PR). They do not validate available cash or quantity.
 */
public final class Account {

    private BigDecimal cash = BigDecimal.ZERO;
    private BigDecimal reservedCash = BigDecimal.ZERO;
    private final Map<Long, Position> positions = new LinkedHashMap<>();

    private Account() {}

    public static Account empty() {
        return new Account();
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

    public Optional<Position> position(long instrumentId) {
        return Optional.ofNullable(positions.get(instrumentId));
    }

    public long availableQuantity(long instrumentId) {
        Position position = positions.get(instrumentId);
        return position == null ? 0L : position.availableQuantity();
    }

    public void applyBuyFill(long instrumentId, long quantity, BigDecimal price) {
        BigDecimal notional = notional(quantity, price);
        cash = cash.subtract(notional);
        positionFor(instrumentId).applyBuy(quantity);
    }

    public void applySellFill(long instrumentId, long quantity, BigDecimal price) {
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

    public void reserveShares(long instrumentId, long quantity) {
        positionFor(instrumentId).reserve(quantity);
    }

    public void releaseShares(long instrumentId, long quantity) {
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
     * Rebuilds an account from the user's ledger. Applies the same transitions as incremental
     * updates, using each entry's current status only.
     */
    public static Account rebuildFrom(Collection<LedgerEntry> entries) {
        Objects.requireNonNull(entries, "entries");
        Account account = empty();
        entries.stream()
                .sorted(Comparator.comparing(LedgerEntry::datetime).thenComparingLong(LedgerEntry::id))
                .forEach(account::apply);
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
            case BUY -> applyBuyFill(entry.instrumentId(), shareQuantity(entry), entry.price());
            case SELL -> applySellFill(entry.instrumentId(), shareQuantity(entry), entry.price());
            case CASH_IN -> cashIn(entry.size());
            case CASH_OUT -> cashOut(entry.size());
        }
    }

    private void applyNew(LedgerEntry entry) {
        if (entry.type() != OrderType.LIMIT) {
            return;
        }
        switch (entry.side()) {
            case BUY -> reserveCash(entry.size().multiply(entry.price()));
            case SELL -> reserveShares(entry.instrumentId(), shareQuantity(entry));
            case CASH_IN, CASH_OUT -> {
                // Transfers are not reserved via NEW LIMIT orders.
            }
        }
    }

    private Position positionFor(long instrumentId) {
        return positions.computeIfAbsent(instrumentId, Position::new);
    }

    private static long shareQuantity(LedgerEntry entry) {
        try {
            return entry.size().longValueExact();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("share quantity must be a whole number", ex);
        }
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
}
