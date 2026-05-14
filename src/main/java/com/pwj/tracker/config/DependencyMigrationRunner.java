package com.pwj.tracker.config;

import com.pwj.tracker.repository.PwjEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DependencyMigrationRunner implements ApplicationRunner {

    private final PwjEntryRepository repository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Step 1: ensure the column exists — Railway MySQL may not have it
        // if ddl-auto=update lacked ALTER TABLE permission
        try {
            jdbcTemplate.execute("ALTER TABLE pwj_entry ADD COLUMN dependency VARCHAR(300)");
            log.info("dependency column created in pwj_entry");
        } catch (Exception e) {
            // Column already exists — safe to ignore
        }

        // Step 2: backfill any rows that still have null/blank dependency
        int updated = repository.backfillNullDependency();
        if (updated > 0) {
            log.info("Backfilled dependency='OH Approval' for {} entries", updated);
        }
    }
}
