package com.cocoscap.challenge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
class CocosChallengeApplicationSmokeTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

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
        assertThat(count("marketdata")).isEqualTo(126);
    }

    private int count(String table) {
        return jdbcClient
                .sql("SELECT COUNT(*) FROM " + table)
                .query(Integer.class)
                .single();
    }
}
