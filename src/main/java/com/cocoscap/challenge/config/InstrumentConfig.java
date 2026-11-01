package com.cocoscap.challenge.config;

import com.cocoscap.challenge.adapter.out.persistence.instrument.JdbcInstrumentSearchAdapter;
import com.cocoscap.challenge.application.instrument.InstrumentSearchPort;
import com.cocoscap.challenge.application.instrument.SearchInstrumentsUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration(proxyBeanMethods = false)
public class InstrumentConfig {

    @Bean
    InstrumentSearchPort instrumentSearchPort(JdbcClient jdbcClient) {
        return new JdbcInstrumentSearchAdapter(jdbcClient);
    }

    @Bean
    SearchInstrumentsUseCase searchInstrumentsUseCase(InstrumentSearchPort instrumentSearchPort) {
        return new SearchInstrumentsUseCase(instrumentSearchPort);
    }
}
