package com.cocoscap.challenge.domain.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocoscap.challenge.domain.costbasis.CostBasisCalculator;
import com.cocoscap.challenge.domain.costbasis.WeightedAverageCostBasisCalculator;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountIdentityTest {

    private static final CostBasisCalculator COST_BASIS = new WeightedAverageCostBasisCalculator();

    @Test
    @DisplayName("Accounts with the same id are equal even with different state")
    void sameIdEqualDespiteDifferentState() {
        Account left = Account.empty(AccountId.of(1L), COST_BASIS);
        left.cashIn(new BigDecimal("100"));
        Account right = Account.empty(AccountId.of(1L), COST_BASIS);
        right.cashIn(new BigDecimal("999"));

        assertThat(left).isEqualTo(right);
        assertThat(left).hasSameHashCodeAs(right);
    }

    @Test
    @DisplayName("Accounts with different ids are not equal even with the same state")
    void differentIdNotEqualWithSameState() {
        Account left = Account.empty(AccountId.of(1L), COST_BASIS);
        left.cashIn(new BigDecimal("100"));
        Account right = Account.empty(AccountId.of(2L), COST_BASIS);
        right.cashIn(new BigDecimal("100"));

        assertThat(left).isNotEqualTo(right);
    }
}
