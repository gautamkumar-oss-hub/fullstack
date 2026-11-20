package com.hospital.appointment.config;

import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class DatabaseTimeZoneConfiguration {

    @Bean
    FlywayConfigurationCustomizer postgresUtcFlywayCustomizer() {
        return (FluentConfiguration configuration) -> configuration.initSql("SET TIME ZONE 'UTC'");
    }
}
