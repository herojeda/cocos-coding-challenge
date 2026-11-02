package com.cocoscap.challenge.application.observability;

import com.cocoscap.challenge.domain.order.OrderSide;
import com.cocoscap.challenge.domain.order.OrderStatus;
import com.cocoscap.challenge.domain.order.OrderType;
import com.cocoscap.challenge.domain.order.RejectionReason;
import java.time.Duration;
import org.springframework.lang.Nullable;

/** Business metrics for trading flows (ADR-009). No high-cardinality tags. */
public interface TradingMetricsPort {

    void projectionRebuilt(Duration duration, boolean success);

    /** {@code trading.orders}. The adapter maps a null rejection reason to the tag {@code NONE}. */
    void orderProcessed(OrderType type, OrderSide side, OrderStatus status, @Nullable RejectionReason rejectionReason);

    /** {@code trading.account.busy}. The adapter maps the operation to the lowercase tag value. */
    void accountBusy(AccountOperation operation);
}
