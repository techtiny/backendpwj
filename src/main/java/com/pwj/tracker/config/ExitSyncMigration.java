package com.pwj.tracker.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Logins marked exited before the Employee Master followed along still have no date of resignation there,
 *  so they kept showing in the Employee Master's Active list — copy the exit date across once. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExitSyncMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int n = jdbcTemplate.update("UPDATE hr_employee_master e JOIN app_user u ON u.username = e.linked_username " +
                    "SET e.dor = u.exit_date WHERE u.exited = 1 AND u.exit_date IS NOT NULL AND e.dor IS NULL");
            if (n > 0) log.info("Recorded the exit date on {} Employee Master profile(s) of exited logins", n);
        } catch (Exception e) {
            log.warn("Skipped exited-login Employee Master sync: {}", e.getMessage());
        }
    }
}
