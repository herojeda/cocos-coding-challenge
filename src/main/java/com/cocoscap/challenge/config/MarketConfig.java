package com.cocoscap.challenge.config;

import com.cocoscap.challenge.adapter.out.persistence.market.JdbcMarketDataAdapter;
import com.cocoscap.challenge.application.market.MarketDataPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration(proxyBeanMethods = false)
public class MarketConfig {

    @Bean
    MarketDataPort marketDataPort(JdbcClient jdbcClient) {
        return new JdbcMarketDataAdapter(jdbcClient);
    }
}
