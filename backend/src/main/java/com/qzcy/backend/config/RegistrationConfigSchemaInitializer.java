package com.qzcy.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RegistrationConfigSchemaInitializer implements CommandLineRunner {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS registration_config (
                    id BIGINT PRIMARY KEY,
                    registration_closed TINYINT(1) NOT NULL DEFAULT 0,
                    invitation_only TINYINT(1) NOT NULL DEFAULT 0
                )
                """);
        jdbcTemplate.update("INSERT IGNORE INTO registration_config (id) VALUES (1)");
    }
}
