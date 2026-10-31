package com.cocoscap.challenge.domain.account;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class User1SeedAccountTest {

    private static final AccountId USER1_ACCOUNT = AccountId.of(1L);

    @Test
    @DisplayName("ACB-06 · Tenencia negativa en los datos existentes")
    void acb06NegativeBmaHoldingFromSeed() {
        Account account = rebuild();

        assertThat(account.position(User1SeedLedgerFixture.BMA).orElseThrow().quantity()).isEqualTo(-10L);
    }

    @Test
    @DisplayName("ACB-11 · Disponible del usuario de la seed")
    void acb11SeedAvailableCash() {
        Account account = rebuild();

        assertThat(account.cash()).isEqualByComparingTo("753000");
        assertThat(account.availableCash()).isEqualByComparingTo("627500");
        assertThat(account.position(User1SeedLedgerFixture.PAMP).orElseThrow().quantity()).isEqualTo(40L);
        assertThat(account.position(User1SeedLedgerFixture.METR).orElseThrow().quantity()).isEqualTo(500L);
        assertThat(account.position(User1SeedLedgerFixture.BMA).orElseThrow().quantity()).isEqualTo(-10L);
    }

    @Test
    @DisplayName("ACB-13 · Disponible con tenencia negativa")
    void acb13AvailableWithNegativeHolding() {
        Account account = rebuild();

        assertThat(account.availableQuantity(User1SeedLedgerFixture.BMA)).isEqualTo(-10L);
    }

    private static Account rebuild() {
        return Account.rebuildFrom(USER1_ACCOUNT, User1SeedLedgerFixture.entries().stream());
    }
}
