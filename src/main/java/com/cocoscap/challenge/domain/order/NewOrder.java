package com.cocoscap.challenge.domain.order;

import com.cocoscap.challenge.domain.instrument.InstrumentId;
import com.cocoscap.challenge.domain.user.UserId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Validated placement pedido: exactly one of size or amount, LIMIT with price, MARKET without
 * (ORD-01–ORD-05). Instrument existence and {@code CURRENCY} checks stay in the use case.
 */
public record NewOrder(
        UserId userId,
        InstrumentId instrumentId,
        OrderSide side,
        OrderType type,
        Long size,
        BigDecimal amount,
        BigDecimal price) {

    private static final int MONEY_SCALE = 2;

    public NewOrder {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(instrumentId, "instrumentId");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(type, "type");

        if (side != OrderSide.BUY && side != OrderSide.SELL) {
            throw new NewOrderValidationException("side", "side must be BUY or SELL");
        }

        boolean hasSize = size != null;
        boolean hasAmount = amount != null;
        if (hasSize == hasAmount) {
            throw new NewOrderValidationException(
                    hasSize ? "amount" : "size", "exactly one of size or amount is required");
        }
        if (hasSize && size <= 0) {
            throw new NewOrderValidationException("size", "size must be positive");
        }
        if (hasAmount) {
            if (amount.signum() <= 0) {
                throw new NewOrderValidationException("amount", "amount must be positive");
            }
            try {
                amount = amount.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
            } catch (ArithmeticException ex) {
                throw new NewOrderValidationException(
                        "amount", "amount must have at most " + MONEY_SCALE + " decimal places");
            }
        }

        if (type == OrderType.LIMIT) {
            if (price == null) {
                throw new NewOrderValidationException("price", "LIMIT orders require a price");
            }
            if (price.signum() <= 0) {
                throw new NewOrderValidationException("price", "price must be positive");
            }
        } else if (type == OrderType.MARKET) {
            if (price != null) {
                throw new NewOrderValidationException("price", "MARKET orders must not include a price");
            }
        } else {
            throw new NewOrderValidationException("type", "type must be MARKET or LIMIT");
        }
    }

    public boolean byAmount() {
        return amount != null;
    }
}
