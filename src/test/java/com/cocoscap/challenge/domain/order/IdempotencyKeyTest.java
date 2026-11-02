package com.cocoscap.challenge.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdempotencyKeyTest {

    @Test
    @DisplayName("Accepts printable ASCII keys from 1 to 64 characters")
    void acceptsPrintableAsciiWithinLength() {
        assertThat(IdempotencyKey.of("a").value()).isEqualTo("a");
        assertThat(IdempotencyKey.of("k-1").value()).isEqualTo("k-1");
        assertThat(IdempotencyKey.of("x".repeat(64)).value()).hasSize(64);
    }

    @Test
    @DisplayName("Rejects blank, too long, or non-printable keys without echoing the value")
    void rejectsInvalidKeysWithoutEchoingValue() {
        assertInvalid(null);
        assertInvalid("");
        assertInvalid(" ".repeat(1));
        assertInvalid("has space");
        assertInvalid("x".repeat(65));
        assertInvalid("tab\there");
    }

    private static void assertInvalid(String value) {
        assertThatThrownBy(() -> IdempotencyKey.of(value))
                .isInstanceOf(NewOrderValidationException.class)
                .satisfies(ex -> {
                    NewOrderValidationException validation = (NewOrderValidationException) ex;
                    assertThat(validation.field()).isEqualTo("Idempotency-Key");
                    assertThat(validation.getMessage()).isEqualTo("Idempotency-Key is invalid");
                    if (value != null && !value.isBlank()) {
                        assertThat(validation.getMessage()).doesNotContain(value);
                    }
                });
    }
}
