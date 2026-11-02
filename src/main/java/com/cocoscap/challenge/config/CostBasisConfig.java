package com.cocoscap.challenge.config;

import com.cocoscap.challenge.domain.costbasis.CostBasisCalculator;
import com.cocoscap.challenge.domain.costbasis.WeightedAverageCostBasisCalculator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CostBasisConfig {

    @Bean
    CostBasisCalculator costBasisCalculator() {
        return new WeightedAverageCostBasisCalculator();
    }
}
