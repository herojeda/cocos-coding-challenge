package com.cocoscap.challenge;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shares one PostgreSQL container across every Spring test context in the suite.
 * The container is a static singleton; the {@code @Bean} only exposes that instance
 * so distinct contexts (extra {@code @Primary} beans, profiles, etc.) do not start
 * another Postgres.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainersConfiguration {

    private static final PostgreSQLContainer CONTAINER =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    static {
        CONTAINER.start();
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return CONTAINER;
    }
}
