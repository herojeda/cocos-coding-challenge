package com.cocoscap.challenge.application.observability;

import com.cocoscap.challenge.domain.order.OrderSide;
import com.cocoscap.challenge.domain.order.OrderStatus;
import com.cocoscap.challenge.domain.order.OrderType;
import com.cocoscap.challenge.domain.order.RejectionReason;
import java.time.Duration;

/** Business metrics for trading flows (ADR-009). No high-cardinality tags. */
public interface TradingMetricsPort {

    void projectionRebuilt(Duration duration, boolean success);

    /**
     * {@code trading.account.lock.wait}. No tags. Includes the account read that follows the lock;
     * timed waits that end in {@code ACCOUNT_BUSY} are also reported.
     */
    void accountLockWaited(Duration duration);

    /**
     * {@code trading.orders}. {@code rejectionReason} may be {@code null}; the adapter maps that to
     * the tag {@code NONE}.
     */
    void orderProcessed(OrderType type, OrderSide side, OrderStatus status, RejectionReason rejectionReason);

    /** {@code trading.account.busy}. The adapter maps the operation to the lowercase tag value. */
    void accountBusy(AccountOperation operation);

    /**
     * {@code trading.idempotency.replays}. The adapter maps the result to the lowercase tag value
     * ({@code replayed}, {@code key_reused}).
     */
    void idempotencyReplay(IdempotencyReplayResult result);
}
