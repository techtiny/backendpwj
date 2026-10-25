package com.pwj.tracker.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Project types are stored as plain text, so projects tagged with the old names
 *  ("X", "Commercial") are re-tagged to the renamed types. Idempotent. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectTypeMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int a = jdbcTemplate.update("UPDATE project SET project_type = 'Residential Interior' WHERE project_type = 'X'");
            int b = jdbcTemplate.update("UPDATE project SET project_type = 'Commercial Interior' WHERE project_type = 'Commercial'");
            if (a + b > 0) log.info("Re-tagged project types: {} X -> Residential Interior, {} Commercial -> Commercial Interior", a, b);
        } catch (Exception e) {
            log.warn("Skipped project type re-tag: {}", e.getMessage());
        }
    }
}
