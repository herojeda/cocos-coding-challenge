package com.cocoscap.challenge.config;

import com.cocoscap.challenge.adapter.out.persistence.account.JdbcAccountLedgerAdapter;
import com.cocoscap.challenge.adapter.out.persistence.account.JdbcAccountProjectionAdapter;
import com.cocoscap.challenge.application.account.AccountLedgerPort;
import com.cocoscap.challenge.application.account.AccountProjectionPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration(proxyBeanMethods = false)
public class AccountConfig {

    @Bean
    AccountLedgerPort accountLedgerPort(
            JdbcClient jdbcClient, @Value("${cocos.persistence.ledger-fetch-size}") int ledgerFetchSize) {
        return new JdbcAccountLedgerAdapter(jdbcClient, ledgerFetchSize);
    }

    @Bean
    AccountProjectionPort accountProjectionPort(JdbcClient jdbcClient) {
        return new JdbcAccountProjectionAdapter(jdbcClient);
    }
}
