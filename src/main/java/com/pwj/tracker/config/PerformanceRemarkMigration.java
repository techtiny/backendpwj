package com.pwj.tracker.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** hbm2ddl "update" never drops constraints, so databases created when remarks were limited to
 *  one per employee per day still carry that unique index — drop it so several can be added. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PerformanceRemarkMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            var names = jdbcTemplate.queryForList(
                    "SELECT DISTINCT index_name FROM information_schema.statistics " +
                    "WHERE table_schema = DATABASE() AND table_name = 'hr_performance_remark' AND non_unique = 0 " +
                    "AND index_name <> 'PRIMARY'", String.class);
            for (String n : names) {
                jdbcTemplate.execute("ALTER TABLE hr_performance_remark DROP INDEX `" + n + "`");
                log.info("Dropped unique index {} on hr_performance_remark", n);
            }
        } catch (Exception e) {
            log.warn("Skipped hr_performance_remark unique-index cleanup: {}", e.getMessage());
        }
        // Rating became optional (plain comments); hbm2ddl "update" won't relax an existing NOT NULL.
        try {
            jdbcTemplate.execute("ALTER TABLE hr_performance_remark MODIFY COLUMN rating INT NULL");
        } catch (Exception e) {
            log.warn("Skipped hr_performance_remark.rating nullable change: {}", e.getMessage());
        }
    }
}
