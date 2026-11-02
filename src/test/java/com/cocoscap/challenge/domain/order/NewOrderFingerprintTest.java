package com.cocoscap.challenge.domain.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocoscap.challenge.domain.instrument.InstrumentId;
import com.cocoscap.challenge.domain.user.UserId;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NewOrderFingerprintTest {

    private static final UserId USER = UserId.of(1L);
    private static final InstrumentId GGAL = InstrumentId.of(34L);

    @Test
    @DisplayName("Same order with different decimal scales shares one fingerprint")
    void sameFingerprintAcrossDecimalScales() {
        RequestHash fromInt = limitBuy(new BigDecimal("850")).fingerprint();
        RequestHash fromOneScale = limitBuy(new BigDecimal("850.0")).fingerprint();
        RequestHash fromTwoScale = limitBuy(new BigDecimal("850.00")).fingerprint();

        assertThat(fromInt).isEqualTo(fromOneScale).isEqualTo(fromTwoScale);
        assertThat(fromInt.value()).hasSize(64);
    }

    @Test
    @DisplayName("Fingerprint changes when any defining field changes")
    void fingerprintDiffersWhenFieldsChange() {
        RequestHash base = limitBuy(new BigDecimal("850.00")).fingerprint();

        assertThat(new NewOrder(USER, InstrumentId.of(47L), OrderSide.BUY, OrderType.LIMIT, 10L, null, money("850"))
                        .fingerprint())
                .isNotEqualTo(base);
        assertThat(new NewOrder(USER, GGAL, OrderSide.SELL, OrderType.LIMIT, 10L, null, money("850"))
                        .fingerprint())
                .isNotEqualTo(base);
        assertThat(new NewOrder(USER, GGAL, OrderSide.BUY, OrderType.MARKET, 10L, null, null).fingerprint())
                .isNotEqualTo(base);
        assertThat(new NewOrder(USER, GGAL, OrderSide.BUY, OrderType.LIMIT, 11L, null, money("850"))
                        .fingerprint())
                .isNotEqualTo(base);
        assertThat(new NewOrder(USER, GGAL, OrderSide.BUY, OrderType.LIMIT, 10L, null, money("851"))
                        .fingerprint())
                .isNotEqualTo(base);
        assertThat(new NewOrder(USER, GGAL, OrderSide.BUY, OrderType.MARKET, null, money("10"), null)
                        .fingerprint())
                .isNotEqualTo(new NewOrder(USER, GGAL, OrderSide.BUY, OrderType.MARKET, 10L, null, null)
                        .fingerprint());
    }

    @Test
    @DisplayName("User is not part of the fingerprint")
    void userIsExcludedFromFingerprint() {
        NewOrder first = new NewOrder(UserId.of(1L), GGAL, OrderSide.BUY, OrderType.LIMIT, 10L, null, money("850"));
        NewOrder second = new NewOrder(UserId.of(2L), GGAL, OrderSide.BUY, OrderType.LIMIT, 10L, null, money("850"));
        assertThat(first.fingerprint()).isEqualTo(second.fingerprint());
    }

    private static NewOrder limitBuy(BigDecimal price) {
        return new NewOrder(USER, GGAL, OrderSide.BUY, OrderType.LIMIT, 10L, null, price);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
