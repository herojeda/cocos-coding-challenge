package com.cocoscap.challenge.config;

import com.cocoscap.challenge.adapter.out.persistence.instrument.InstrumentSearchJdbcAdapter;
import com.cocoscap.challenge.application.instrument.InstrumentSearchPort;
import com.cocoscap.challenge.application.instrument.SearchInstruments;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration(proxyBeanMethods = false)
public class ApplicationConfiguration {

    @Bean
    InstrumentSearchPort instrumentSearchPort(JdbcClient jdbcClient) {
        return new InstrumentSearchJdbcAdapter(jdbcClient);
    }

    @Bean
    SearchInstruments searchInstruments(InstrumentSearchPort instrumentSearchPort) {
        return new SearchInstruments(instrumentSearchPort);
    }
}
