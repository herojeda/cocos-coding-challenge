package com.cocoscap.challenge.domain.account;

import java.math.BigDecimal;

/** Sole place that computes average cost and realized PnL (ADR-003, S2). */
public interface CostBasisCalculator {

    CostBasisState applyBuy(CostBasisState current, long quantity, BigDecimal price);

    CostBasisState applySell(CostBasisState current, long quantity, BigDecimal price);
}
