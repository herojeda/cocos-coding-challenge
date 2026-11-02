package com.cocoscap.challenge.config;

import com.cocoscap.challenge.adapter.out.persistence.order.JdbcOrderBookAdapter;
import com.cocoscap.challenge.application.order.OrderBookPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration(proxyBeanMethods = false)
public class OrderConfig {

    @Bean
    OrderBookPort orderBookPort(JdbcClient jdbcClient) {
        return new JdbcOrderBookAdapter(jdbcClient);
    }
}
