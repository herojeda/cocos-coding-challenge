package com.cocoscap.challenge.config;

import com.cocoscap.challenge.application.account.AccountLedgerPort;
import com.cocoscap.challenge.application.account.AccountProjectionPort;
import com.cocoscap.challenge.application.instrument.InstrumentLookupPort;
import com.cocoscap.challenge.application.market.MarketDataPort;
import com.cocoscap.challenge.application.portfolio.GetPortfolioUseCase;
import com.cocoscap.challenge.domain.costbasis.CostBasisCalculator;
import com.cocoscap.challenge.domain.costbasis.WeightedAverageCostBasisCalculator;
import com.cocoscap.challenge.domain.portfolio.PortfolioValuation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PortfolioConfig {

    @Bean
    CostBasisCalculator costBasisCalculator() {
        return new WeightedAverageCostBasisCalculator();
    }

    @Bean
    PortfolioValuation portfolioValuation() {
        return new PortfolioValuation();
    }

    @Bean
    GetPortfolioUseCase getPortfolioUseCase(
            AccountProjectionPort accountProjectionPort,
            AccountLedgerPort accountLedgerPort,
            InstrumentLookupPort instrumentLookupPort,
            MarketDataPort marketDataPort,
            CostBasisCalculator costBasisCalculator,
            PortfolioValuation portfolioValuation) {
        return new GetPortfolioUseCase(
                accountProjectionPort,
                accountLedgerPort,
                instrumentLookupPort,
                marketDataPort,
                costBasisCalculator,
                portfolioValuation);
    }
}
