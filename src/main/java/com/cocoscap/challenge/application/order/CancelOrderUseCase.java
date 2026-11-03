package com.cocoscap.challenge.application.order;

import com.cocoscap.challenge.application.account.AccountForUpdateLoader;
import com.cocoscap.challenge.application.account.AccountProjectionPort;
import com.cocoscap.challenge.application.observability.AccountOperation;
import com.cocoscap.challenge.application.observability.TradingMetricsPort;
import com.cocoscap.challenge.domain.account.Account;
import com.cocoscap.challenge.domain.account.AccountBusyException;
import com.cocoscap.challenge.domain.order.Order;
import com.cocoscap.challenge.domain.order.OrderId;
import com.cocoscap.challenge.domain.order.OrderNotFoundException;
import com.cocoscap.challenge.domain.user.UserId;
import com.cocoscap.challenge.domain.user.UserNotFoundException;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cancels a {@code NEW} order under the account lock (ADR-006) and releases its reservation.
 *
 * <p>Not {@code final} so Spring can apply the transactional proxy.
 */
public class CancelOrderUseCase {

    private final AccountForUpdateLoader accountForUpdateLoader;
    private final AccountProjectionPort accountProjectionPort;
    private final OrderBookPort orderBookPort;
    private final TradingMetricsPort tradingMetricsPort;

    public CancelOrderUseCase(
            AccountForUpdateLoader accountForUpdateLoader,
            AccountProjectionPort accountProjectionPort,
            OrderBookPort orderBookPort,
            TradingMetricsPort tradingMetricsPort) {
        this.accountForUpdateLoader =
                Objects.requireNonNull(accountForUpdateLoader, "accountForUpdateLoader");
        this.accountProjectionPort =
                Objects.requireNonNull(accountProjectionPort, "accountProjectionPort");
        this.orderBookPort = Objects.requireNonNull(orderBookPort, "orderBookPort");
        this.tradingMetricsPort = Objects.requireNonNull(tradingMetricsPort, "tradingMetricsPort");
    }

    @Transactional
    public Order execute(UserId userId, OrderId orderId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(orderId, "orderId");
        try {
            Account account = accountForUpdateLoader
                    .load(userId)
                    .orElseThrow(() -> new UserNotFoundException(userId));

            Order order = orderBookPort
                    .findByUserAndId(userId, orderId)
                    .orElseThrow(OrderNotFoundException::new);

            Order cancelled = order.cancel();
            account.release(cancelled);
            orderBookPort.markCancelled(orderId);
            accountProjectionPort.save(account.state());
            return cancelled;
        } catch (AccountBusyException ex) {
            tradingMetricsPort.accountBusy(AccountOperation.CANCEL);
            throw ex;
        }
    }
}
