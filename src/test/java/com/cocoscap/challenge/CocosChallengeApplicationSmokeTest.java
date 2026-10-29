package com.cocoscap.challenge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
class CocosChallengeApplicationSmokeTest {

    @Autowired
    JdbcClient jdbcClient;

    @Test
    void contextLoadsAndAppliesCocosSeed() {
        Integer appliedMigrations = jdbcClient
                .sql("SELECT COUNT(*) FROM flyway_schema_history WHERE success AND version = '1'")
                .query(Integer.class)
                .single();
        assertThat(appliedMigrations).isEqualTo(1);

        assertThat(count("users")).isEqualTo(4);
        assertThat(count("instruments")).isEqualTo(66);
        assertThat(count("orders")).isEqualTo(11);
        assertThat(count("market_data")).isEqualTo(126);
    }

    private int count(String table) {
        return jdbcClient
                .sql("SELECT COUNT(*) FROM " + table)
                .query(Integer.class)
                .single();
    }
}
