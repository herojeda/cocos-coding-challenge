package com.cocoscap.challenge.application.order;

import com.cocoscap.challenge.application.account.AccountForUpdateLoader;
import com.cocoscap.challenge.application.account.AccountProjectionPort;
import com.cocoscap.challenge.application.instrument.InstrumentLookupPort;
import com.cocoscap.challenge.application.market.MarketDataPort;
import com.cocoscap.challenge.application.observability.AccountOperation;
import com.cocoscap.challenge.application.observability.TradingMetricsPort;
import com.cocoscap.challenge.domain.account.Account;
import com.cocoscap.challenge.domain.account.AccountBusyException;
import com.cocoscap.challenge.domain.instrument.Instrument;
import com.cocoscap.challenge.domain.instrument.InstrumentId;
import com.cocoscap.challenge.domain.instrument.InstrumentNotFoundException;
import com.cocoscap.challenge.domain.instrument.InstrumentType;
import com.cocoscap.challenge.domain.market.Quote;
import com.cocoscap.challenge.domain.order.IdempotencyKey;
import com.cocoscap.challenge.domain.order.NewOrder;
import com.cocoscap.challenge.domain.order.NewOrderValidationException;
import com.cocoscap.challenge.domain.order.Order;
import com.cocoscap.challenge.domain.order.OrderDecision;
import com.cocoscap.challenge.domain.order.OrderId;
import com.cocoscap.challenge.domain.order.OrderPlacementPolicy;
import com.cocoscap.challenge.domain.order.OrderStatus;
import com.cocoscap.challenge.domain.user.UserNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * Places an order under the account lock (ADR-006). Idempotency lookup lands in a follow-up PR; this
 * version only stores the key and fingerprint with the registered order.
 *
 * <p>Not {@code final} so Spring can apply the transactional proxy.
 */
public class PlaceOrderUseCase {

    private final AccountForUpdateLoader accountForUpdateLoader;
    private final AccountProjectionPort accountProjectionPort;
    private final OrderBookPort orderBookPort;
    private final InstrumentLookupPort instrumentLookupPort;
    private final MarketDataPort marketDataPort;
    private final TradingMetricsPort tradingMetricsPort;
    private final Clock clock;

    public PlaceOrderUseCase(
            AccountForUpdateLoader accountForUpdateLoader,
            AccountProjectionPort accountProjectionPort,
            OrderBookPort orderBookPort,
            InstrumentLookupPort instrumentLookupPort,
            MarketDataPort marketDataPort,
            TradingMetricsPort tradingMetricsPort,
            Clock clock) {
        this.accountForUpdateLoader =
                Objects.requireNonNull(accountForUpdateLoader, "accountForUpdateLoader");
        this.accountProjectionPort =
                Objects.requireNonNull(accountProjectionPort, "accountProjectionPort");
        this.orderBookPort = Objects.requireNonNull(orderBookPort, "orderBookPort");
        this.instrumentLookupPort = Objects.requireNonNull(instrumentLookupPort, "instrumentLookupPort");
        this.marketDataPort = Objects.requireNonNull(marketDataPort, "marketDataPort");
        this.tradingMetricsPort = Objects.requireNonNull(tradingMetricsPort, "tradingMetricsPort");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public Order execute(NewOrder newOrder, IdempotencyKey idempotencyKey) {
        Objects.requireNonNull(newOrder, "newOrder");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        try {
            Account account = accountForUpdateLoader
                    .load(newOrder.userId())
                    .orElseThrow(() -> new UserNotFoundException(newOrder.userId()));

            Instrument instrument = requireTradableInstrument(newOrder.instrumentId());
            Optional<Quote> quote = Optional.ofNullable(
                    marketDataPort.findLatestByInstrumentIds(List.of(instrument.id())).get(instrument.id()));

            OrderDecision decision = OrderPlacementPolicy.decide(
                    newOrder,
                    account.availableCash(),
                    account.availableQuantity(instrument.id()),
                    quote);

            Instant datetime = clock.instant().truncatedTo(ChronoUnit.MICROS);
            OrderId orderId =
                    orderBookPort.register(decision, idempotencyKey, newOrder.fingerprint(), datetime);
            account.apply(decision);
            // REJECTED leaves the account unchanged (ADR-005), and a PENDING account was already saved by
            // AccountForUpdateLoader when it was rebuilt, so there is nothing to persist.
            if (decision.status() != OrderStatus.REJECTED) {
                accountProjectionPort.save(account.state());
            }

            Order order = decision.toOrder(orderId, datetime);
            tradingMetricsPort.orderProcessed(order.type(), order.side(), order.status(), order.rejectionReason());
            return order;
        } catch (AccountBusyException ex) {
            tradingMetricsPort.accountBusy(AccountOperation.PLACE);
            throw ex;
        }
    }

    private Instrument requireTradableInstrument(InstrumentId instrumentId) {
        Instrument instrument = instrumentLookupPort
                .findByIds(List.of(instrumentId))
                .get(instrumentId);
        if (instrument == null) {
            throw new InstrumentNotFoundException();
        }
        if (instrument.type() == InstrumentType.CURRENCY) {
            throw new NewOrderValidationException("instrument_id", "instrument_id must not be a CURRENCY");
        }
        return instrument;
    }
}
