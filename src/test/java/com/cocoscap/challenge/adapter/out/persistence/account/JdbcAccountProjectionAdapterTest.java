package com.cocoscap.challenge.adapter.out.persistence.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JdbcAccountProjectionAdapterTest {

    @Test
    @DisplayName("lock timeout below 1ms is rejected before reaching PostgreSQL")
    void lockTimeoutBelowOneMillisecondIsRejected() {
        assertThatThrownBy(() -> JdbcAccountProjectionAdapter.toLockTimeoutMillis(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1ms");
        assertThatThrownBy(() -> JdbcAccountProjectionAdapter.toLockTimeoutMillis(Duration.ofNanos(500_000)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1ms");
        assertThatThrownBy(() -> JdbcAccountProjectionAdapter.toLockTimeoutMillis(Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1ms");
    }

    @Test
    @DisplayName("lock timeout is formatted as whole milliseconds for set_config")
    void lockTimeoutIsFormattedAsMilliseconds() {
        assertThat(JdbcAccountProjectionAdapter.toLockTimeoutMillis(Duration.ofSeconds(2))).isEqualTo("2000");
        assertThat(JdbcAccountProjectionAdapter.toLockTimeoutMillis(Duration.ofMillis(200))).isEqualTo("200");
    }
}
