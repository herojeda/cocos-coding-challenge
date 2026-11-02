package com.cocoscap.challenge.domain.order;

import com.cocoscap.challenge.domain.market.Quote;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure placement rules: reference price, size-from-amount, availability checks and initial status
 * (MARKET → {@code FILLED}, LIMIT → {@code NEW}, or {@code REJECTED} with a reason).
 *
 * <p>Takes available cash and shares as values so the {@code order} package does not depend on
 * {@code account} (ADR-003: no cycles between domain subpackages). The caller reads them from
 * {@code Account} and applies the decision with {@code Account.apply(OrderPlacementDecision)}.
 */
public final class OrderPlacementPolicy {

    private OrderPlacementPolicy() {}

    public static OrderPlacementDecision decide(
            NewOrder newOrder,
            BigDecimal availableCash,
            long availableQuantity,
            Optional<Quote> quote) {
        Objects.requireNonNull(newOrder, "newOrder");
        Objects.requireNonNull(availableCash, "availableCash");
        Objects.requireNonNull(quote, "quote");

        if (newOrder.type() == OrderType.MARKET && quote.isEmpty()) {
            return rejected(newOrder, newOrder.size(), null, RejectionReason.NO_MARKET_PRICE);
        }

        BigDecimal referencePrice = referencePrice(newOrder, quote);
        long size = resolveSize(newOrder, referencePrice);
        BigDecimal notional = BigDecimal.valueOf(size).multiply(referencePrice);

        if (newOrder.side() == OrderSide.BUY) {
            if (notional.compareTo(availableCash) > 0) {
                return rejected(newOrder, size, referencePrice, RejectionReason.INSUFFICIENT_CASH);
            }
        } else if (size > availableQuantity) {
            return rejected(newOrder, size, referencePrice, RejectionReason.INSUFFICIENT_HOLDINGS);
        }

        if (newOrder.type() == OrderType.MARKET) {
            return new OrderPlacementDecision(OrderDecision.filled(
                    newOrder.userId(),
                    newOrder.instrumentId(),
                    newOrder.side(),
                    newOrder.type(),
                    size,
                    referencePrice,
                    newOrder.amount()));
        }
        return new OrderPlacementDecision(OrderDecision.accepted(
                newOrder.userId(),
                newOrder.instrumentId(),
                newOrder.side(),
                newOrder.type(),
                size,
                referencePrice,
                newOrder.amount()));
    }

    private static BigDecimal referencePrice(NewOrder newOrder, Optional<Quote> quote) {
        if (newOrder.type() == OrderType.LIMIT) {
            return newOrder.price();
        }
        Quote market = quote.orElseThrow();
        if (!market.instrumentId().equals(newOrder.instrumentId())) {
            throw new IllegalArgumentException("quote instrument does not match the new order");
        }
        return market.close();
    }

    private static long resolveSize(NewOrder newOrder, BigDecimal referencePrice) {
        if (!newOrder.byAmount()) {
            return newOrder.size();
        }
        long size = newOrder.amount().divide(referencePrice, 0, RoundingMode.DOWN).longValueExact();
        if (size == 0L) {
            throw new NewOrderValidationException(
                    "amount", "amount is not enough for one share at the reference price");
        }
        return size;
    }

    private static OrderPlacementDecision rejected(
            NewOrder newOrder, Long size, BigDecimal price, RejectionReason reason) {
        return new OrderPlacementDecision(OrderDecision.rejected(
                newOrder.userId(),
                newOrder.instrumentId(),
                newOrder.side(),
                newOrder.type(),
                size,
                price,
                newOrder.amount(),
                reason));
    }
}
