package com.pwj.tracker.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Hibernate maps an @Enumerated(STRING) field to a native MySQL ENUM column, and ddl-auto=update never adds
 *  values to an existing ENUM. app_user.role was created before newer roles existed (e.g. COSTING_PLANNING_ENGINEER),
 *  so inserting such a user fails with "Data truncated for column 'role'". Convert the column to a plain
 *  VARCHAR(30) — which is what the entity declares — so any current or future Role value fits. Idempotent. */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class AppUserRoleMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            String type = jdbcTemplate.queryForObject(
                    "SELECT DATA_TYPE FROM information_schema.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'app_user' AND COLUMN_NAME = 'role'", String.class);
            if (type != null && !type.equalsIgnoreCase("varchar")) {
                jdbcTemplate.execute("ALTER TABLE app_user MODIFY COLUMN role VARCHAR(30) NOT NULL");
                log.info("app_user.role converted from {} to VARCHAR(30)", type);
            }
        } catch (Exception e) {
            log.warn("Skipped app_user.role column check: {}", e.getMessage());
        }
    }
}
