package com.cocoscap.challenge.config;

import com.cocoscap.challenge.adapter.out.persistence.account.JdbcAccountLedgerAdapter;
import com.cocoscap.challenge.adapter.out.persistence.account.JdbcAccountProjectionAdapter;
import com.cocoscap.challenge.application.account.AccountForUpdateLoader;
import com.cocoscap.challenge.application.account.AccountLedgerPort;
import com.cocoscap.challenge.application.account.AccountProjectionPort;
import com.cocoscap.challenge.application.observability.TradingMetricsPort;
import com.cocoscap.challenge.domain.costbasis.CostBasisCalculator;
import java.time.Duration;
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
    AccountProjectionPort accountProjectionPort(
            JdbcClient jdbcClient,
            @Value("${cocos.persistence.account-lock-timeout}") Duration accountLockTimeout) {
        return new JdbcAccountProjectionAdapter(jdbcClient, accountLockTimeout);
    }

    @Bean
    AccountForUpdateLoader accountForUpdateLoader(
            AccountProjectionPort accountProjectionPort,
            AccountLedgerPort accountLedgerPort,
            CostBasisCalculator costBasisCalculator,
            TradingMetricsPort tradingMetricsPort) {
        return new AccountForUpdateLoader(
                accountProjectionPort, accountLedgerPort, costBasisCalculator, tradingMetricsPort);
    }
}
