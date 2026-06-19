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
        // Step 1: ensure dependency column exists in pwj_entry
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

        // Step 3: ensure designation column exists in app_user
        try {
            jdbcTemplate.execute("ALTER TABLE app_user ADD COLUMN designation VARCHAR(150)");
            log.info("designation column created in app_user");
        } catch (Exception e) {
            // Column already exists — safe to ignore
        }

        // Step 4: seed designations for known users (only if not already set)
        java.util.Map<String, String> designations = new java.util.LinkedHashMap<>();
        designations.put("sakthi",     "Site Engineer");
        designations.put("vidhya",     "Admin Executive");
        designations.put("jagan",      "Senior Site Engineer");
        designations.put("bhaskar",    "Costing and Planning Engineer");
        designations.put("keerthi",    "Site Supervisor");
        designations.put("balaji",     "Senior Site Engineer");
        designations.put("jayakumar", "Project Manager");
        designations.put("sakthivel", "Site Supervisor");
        designations.put("aravind",   "Site Engineer");
        designations.put("sandy",     "Site Engineer");
        designations.put("aakash",    "Procurement Engineer");

        // Step 5: add cash transfer workflow columns to hr_petty_cash
        try {
            jdbcTemplate.execute("ALTER TABLE hr_petty_cash ADD COLUMN cash_transferred_at DATETIME");
        } catch (Exception e) { /* already exists */ }
        try {
            jdbcTemplate.execute("ALTER TABLE hr_petty_cash ADD COLUMN proof_url VARCHAR(500)");
        } catch (Exception e) { /* already exists */ }
        try {
            jdbcTemplate.execute("ALTER TABLE hr_petty_cash ADD COLUMN proof_submitted_at DATETIME");
        } catch (Exception e) { /* already exists */ }
        try {
            jdbcTemplate.execute("ALTER TABLE hr_petty_cash ADD COLUMN proof_urls TEXT");
        } catch (Exception e) { /* already exists */ }

        // Step 6: add eligible_for_accounts flag to project table
        try {
            jdbcTemplate.execute("ALTER TABLE project ADD COLUMN eligible_for_accounts BOOLEAN NOT NULL DEFAULT FALSE");
            log.info("eligible_for_accounts column created in project");
        } catch (Exception e) { /* already exists */ }

        for (java.util.Map.Entry<String, String> e : designations.entrySet()) {
            try {
                int rows = jdbcTemplate.update(
                    "UPDATE app_user SET designation = ? WHERE LOWER(username) = ? AND (designation IS NULL OR designation = '')",
                    e.getValue(), e.getKey().toLowerCase());
                if (rows > 0) log.info("Set designation '{}' for user '{}'", e.getValue(), e.getKey());
            } catch (Exception ex) {
                log.warn("Could not set designation for '{}': {}", e.getKey(), ex.getMessage());
            }
        }
    }
}
