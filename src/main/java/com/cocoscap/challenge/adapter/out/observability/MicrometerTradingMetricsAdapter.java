package com.cocoscap.challenge.adapter.out.observability;

import com.cocoscap.challenge.application.observability.AccountOperation;
import com.cocoscap.challenge.application.observability.TradingMetricsPort;
import com.cocoscap.challenge.domain.order.OrderSide;
import com.cocoscap.challenge.domain.order.OrderStatus;
import com.cocoscap.challenge.domain.order.OrderType;
import com.cocoscap.challenge.domain.order.RejectionReason;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import org.springframework.lang.Nullable;

public final class MicrometerTradingMetricsAdapter implements TradingMetricsPort {

    private static final String PROJECTION_REBUILDS = "trading.projection.rebuilds";
    private static final String ORDERS = "trading.orders";
    private static final String ACCOUNT_BUSY = "trading.account.busy";
    private static final String NO_REJECTION_TAG = "NONE";

    private final MeterRegistry meterRegistry;

    public MicrometerTradingMetricsAdapter(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
    }

    @Override
    public void projectionRebuilt(Duration duration, boolean success) {
        Objects.requireNonNull(duration, "duration");
        Timer.builder(PROJECTION_REBUILDS)
                .tag("outcome", success ? "success" : "failure")
                .register(meterRegistry)
                .record(duration);
    }

    @Override
    public void orderProcessed(
            OrderType type, OrderSide side, OrderStatus status, @Nullable RejectionReason rejectionReason) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(status, "status");
        meterRegistry
                .counter(
                        ORDERS,
                        "type",
                        type.name(),
                        "side",
                        side.name(),
                        "status",
                        status.name(),
                        "rejection_reason",
                        rejectionReason == null ? NO_REJECTION_TAG : rejectionReason.name())
                .increment();
    }

    @Override
    public void accountBusy(AccountOperation operation) {
        Objects.requireNonNull(operation, "operation");
        meterRegistry
                .counter(ACCOUNT_BUSY, "operation", operation.name().toLowerCase(Locale.ROOT))
                .increment();
    }
}
