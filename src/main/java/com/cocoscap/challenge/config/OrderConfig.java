package com.cocoscap.challenge.config;

import com.cocoscap.challenge.adapter.out.persistence.order.JdbcOrderBookAdapter;
import com.cocoscap.challenge.application.account.AccountForUpdateLoader;
import com.cocoscap.challenge.application.account.AccountProjectionPort;
import com.cocoscap.challenge.application.instrument.InstrumentLookupPort;
import com.cocoscap.challenge.application.market.MarketDataPort;
import com.cocoscap.challenge.application.observability.TradingMetricsPort;
import com.cocoscap.challenge.application.order.CancelOrderUseCase;
import com.cocoscap.challenge.application.order.OrderBookPort;
import com.cocoscap.challenge.application.order.PlaceOrderUseCase;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration(proxyBeanMethods = false)
public class OrderConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    OrderBookPort orderBookPort(JdbcClient jdbcClient) {
        return new JdbcOrderBookAdapter(jdbcClient);
    }

    @Bean
    PlaceOrderUseCase placeOrderUseCase(
            AccountForUpdateLoader accountForUpdateLoader,
            AccountProjectionPort accountProjectionPort,
            OrderBookPort orderBookPort,
            InstrumentLookupPort instrumentLookupPort,
            MarketDataPort marketDataPort,
            TradingMetricsPort tradingMetricsPort,
            Clock clock) {
        return new PlaceOrderUseCase(
                accountForUpdateLoader,
                accountProjectionPort,
                orderBookPort,
                instrumentLookupPort,
                marketDataPort,
                tradingMetricsPort,
                clock);
    }

    @Bean
    CancelOrderUseCase cancelOrderUseCase(
            AccountForUpdateLoader accountForUpdateLoader,
            AccountProjectionPort accountProjectionPort,
            OrderBookPort orderBookPort) {
        return new CancelOrderUseCase(accountForUpdateLoader, accountProjectionPort, orderBookPort);
    }
}
