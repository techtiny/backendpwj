package com.pwj.tracker.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** hbm2ddl "update" doesn't widen existing columns: dependency used to be VARCHAR(20), but
 *  "Others" accepts free text, so widen it. Idempotent. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleActivityMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("ALTER TABLE ops_schedule_activity MODIFY COLUMN dependency VARCHAR(100)");
        } catch (Exception e) {
            log.warn("Skipped ops_schedule_activity.dependency widening: {}", e.getMessage());
        }
    }
}
