package com.cocoscap.challenge.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cocoscap.challenge.domain.account.AccountState.PositionState;
import com.cocoscap.challenge.domain.costbasis.CostBasisCalculator;
import com.cocoscap.challenge.domain.costbasis.CostBasisState;
import com.cocoscap.challenge.domain.costbasis.WeightedAverageCostBasisCalculator;
import com.cocoscap.challenge.domain.instrument.InstrumentId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountRestoreTest {

    private static final AccountId ACCOUNT = AccountId.of(1L);
    private static final InstrumentId BMA = User1SeedLedgerFixture.BMA;
    private static final InstrumentId PAMP = User1SeedLedgerFixture.PAMP;
    private static final CostBasisCalculator COST_BASIS = new WeightedAverageCostBasisCalculator();

    @Test
    @DisplayName("restore(state) round-trips the seed account including short BMA")
    void restoreRoundTripsSeedState() {
        Account original =
                Account.rebuildFrom(ACCOUNT, User1SeedLedgerFixture.entries().stream(), COST_BASIS);
        AccountState state = original.state();

        Account restored = Account.restore(state, COST_BASIS);

        assertThat(restored.state()).isEqualTo(state);
        Position bma = restored.position(BMA).orElseThrow();
        assertThat(bma.quantity()).isEqualTo(-10L);
        assertThat(bma.averageCost()).isEmpty();
        assertThat(bma.realizedPnl()).isEqualByComparingTo("-200");
    }

    @Test
    @DisplayName("restore rejects negative reservedCash")
    void restoreRejectsNegativeReservedCash() {
        assertThatThrownBy(() -> Account.restore(
                        new AccountState(ACCOUNT, BigDecimal.ZERO, new BigDecimal("-0.01"), List.of()),
                        COST_BASIS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reservedCash");
    }

    @Test
    @DisplayName("restore rejects negative reservedQuantity")
    void restoreRejectsNegativeReservedQuantity() {
        assertThatThrownBy(() -> Account.restore(stateWith(position(PAMP, 10L, -1L, "100.000000", "0.00")), COST_BASIS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reservedQuantity");
    }

    @Test
    @DisplayName("restore rejects missing averageCost when quantity is positive")
    void restoreRejectsMissingAverageCostForPositiveQuantity() {
        assertThatThrownBy(() -> Account.restore(
                        stateWith(new PositionState(
                                PAMP, 10L, 0L, Optional.empty(), scale2("0"))),
                        COST_BASIS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("averageCost");
    }

    @Test
    @DisplayName("restore rejects present averageCost when quantity is not positive")
    void restoreRejectsPresentAverageCostForNonPositiveQuantity() {
        assertThatThrownBy(() -> Account.restore(
                        stateWith(new PositionState(
                                BMA, -10L, 0L, Optional.of(scale6("1500")), scale2("-200"))),
                        COST_BASIS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("averageCost");
    }

    @Test
    @DisplayName("restore rejects averageCost with wrong scale")
    void restoreRejectsAverageCostWrongScale() {
        assertThatThrownBy(() -> Account.restore(
                        stateWith(new PositionState(
                                PAMP, 10L, 0L, Optional.of(new BigDecimal("100.00")), scale2("0"))),
                        COST_BASIS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("averageCost");
    }

    @Test
    @DisplayName("restore rejects realizedPnl with wrong scale")
    void restoreRejectsRealizedPnlWrongScale() {
        assertThatThrownBy(() -> Account.restore(
                        stateWith(new PositionState(
                                PAMP, 10L, 0L, Optional.of(scale6("100")), new BigDecimal("0"))),
                        COST_BASIS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("realizedPnl");
    }

    @Test
    @DisplayName("restore rejects duplicate instrumentId")
    void restoreRejectsDuplicateInstrumentId() {
        PositionState once = position(PAMP, 10L, 0L, "100.000000", "0.00");
        assertThatThrownBy(() -> Account.restore(stateWith(once, once), COST_BASIS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    @DisplayName("state normalizes cash scale so 100 equals 100.00")
    void stateNormalizesCashScale() {
        AccountState scaleZero = new AccountState(
                ACCOUNT, new BigDecimal("100"), BigDecimal.ZERO, List.of());
        AccountState scaleTwo = new AccountState(
                ACCOUNT, new BigDecimal("100.00"), BigDecimal.ZERO.setScale(2), List.of());

        assertThat(scaleZero).isEqualTo(scaleTwo);
        assertThat(scaleZero.cash().scale()).isEqualTo(AccountState.CASH_SCALE);
        assertThat(scaleZero.reservedCash().scale()).isEqualTo(AccountState.CASH_SCALE);
    }

    @Test
    @DisplayName("state rejects cash with more than two decimal places")
    void stateRejectsCashWithExcessScale() {
        assertThatThrownBy(() -> new AccountState(
                        ACCOUNT, new BigDecimal("100.001"), BigDecimal.ZERO, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cash");
    }

    private static AccountState stateWith(PositionState... positions) {
        return new AccountState(ACCOUNT, BigDecimal.ZERO, BigDecimal.ZERO, List.of(positions));
    }

    private static PositionState position(
            InstrumentId instrumentId, long quantity, long reserved, String averageCost, String realizedPnl) {
        return new PositionState(
                instrumentId,
                quantity,
                reserved,
                Optional.of(scale6(averageCost)),
                scale2(realizedPnl));
    }

    private static BigDecimal scale6(String value) {
        return new BigDecimal(value).setScale(CostBasisState.AVERAGE_COST_SCALE);
    }

    private static BigDecimal scale2(String value) {
        return new BigDecimal(value).setScale(CostBasisState.REALIZED_PNL_SCALE);
    }
}
