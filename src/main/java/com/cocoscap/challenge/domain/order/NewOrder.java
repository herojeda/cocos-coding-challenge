package com.cocoscap.challenge.domain.order;

import com.cocoscap.challenge.domain.instrument.InstrumentId;
import com.cocoscap.challenge.domain.user.UserId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Validated placement request: exactly one of size or amount, LIMIT with price, MARKET without
 * (ORD-01–ORD-05). Instrument existence and {@code CURRENCY} checks stay in the use case.
 *
 * <p>Money fields match the column limits: {@code amount} is {@code NUMERIC(18,2)} and
 * {@code price} is {@code NUMERIC(10,2)}. Values are normalized to scale 2 with
 * {@link RoundingMode#UNNECESSARY} so a silent DB round cannot diverge from the reservation.
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
    /** {@code NUMERIC(18,2)} → at most 16 digits before the decimal. */
    private static final int AMOUNT_MAX_INTEGER_DIGITS = 16;
    /** {@code NUMERIC(10,2)} → at most 8 digits before the decimal. */
    private static final int PRICE_MAX_INTEGER_DIGITS = 8;

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
            amount = requireMoney("amount", amount, AMOUNT_MAX_INTEGER_DIGITS);
        }

        if (type == OrderType.LIMIT) {
            if (price == null) {
                throw new NewOrderValidationException("price", "LIMIT orders require a price");
            }
            price = requireMoney("price", price, PRICE_MAX_INTEGER_DIGITS);
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

    private static BigDecimal requireMoney(String field, BigDecimal value, int maxIntegerDigits) {
        if (value.signum() <= 0) {
            throw new NewOrderValidationException(field, field + " must be positive");
        }
        BigDecimal normalized;
        try {
            normalized = value.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new NewOrderValidationException(
                    field, field + " must have at most " + MONEY_SCALE + " decimal places");
        }
        int integerDigits = normalized.precision() - normalized.scale();
        if (integerDigits > maxIntegerDigits) {
            throw new NewOrderValidationException(field, field + " is out of range");
        }
        return normalized;
    }
}
