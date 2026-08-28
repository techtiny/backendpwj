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

        // Step 7: employee number on app_user — add column + backfill EMP0001-style codes
        try {
            jdbcTemplate.execute("ALTER TABLE app_user ADD COLUMN employee_number VARCHAR(20)");
            log.info("employee_number column created in app_user");
        } catch (Exception e) { /* already exists */ }
        try {
            int rows = jdbcTemplate.update(
                "UPDATE app_user SET employee_number = CONCAT('EMP', LPAD(id, 4, '0')) " +
                "WHERE employee_number IS NULL OR employee_number = ''");
            if (rows > 0) log.info("Backfilled employee_number for {} users", rows);
        } catch (Exception e) {
            log.warn("Could not backfill employee_number: {}", e.getMessage());
        }

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

        // Step 8: seed salary structures from the July 2026 salary sheet (Railway prod too).
        // {username -> [monthlyGross, pfApplicable(1/0), ptApplicable(1/0)]}
        java.util.Map<String, Object[]> salaries = new java.util.LinkedHashMap<>();
        salaries.put("vidhya",    new Object[]{23000, 1, 1});
        salaries.put("aakash",    new Object[]{34000, 1, 1});
        salaries.put("sakthi",    new Object[]{37000, 1, 1});
        salaries.put("jagan",     new Object[]{38200, 1, 1});
        salaries.put("bhaskar",   new Object[]{47000, 1, 1});
        salaries.put("jayakumar", new Object[]{62677, 1, 1});
        salaries.put("balaji",    new Object[]{40200, 1, 1});
        salaries.put("keerthi",   new Object[]{22000, 0, 0});
        salaries.put("aravind",   new Object[]{35000, 0, 0});
        salaries.put("sakthivel", new Object[]{20000, 0, 0});
        salaries.put("sandy",     new Object[]{35000, 0, 0});
        for (java.util.Map.Entry<String, Object[]> e : salaries.entrySet()) {
            try {
                int rows = jdbcTemplate.update(
                    "INSERT INTO hr_employee_salary " +
                    "(user_id, employee_number, monthly_gross, pf_applicable, pt_applicable, effective_from, note, created_by, created_at) " +
                    "SELECT au.id, au.employee_number, ?, ?, ?, '2026-07-01', 'Seeded from July 2026 salary sheet', 'system', NOW() " +
                    "FROM app_user au " +
                    "WHERE LOWER(au.username) = ? " +
                    "AND NOT EXISTS (SELECT 1 FROM hr_employee_salary s WHERE s.user_id = au.id)",
                    e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getKey().toLowerCase());
                if (rows > 0) log.info("Seeded salary structure for user '{}'", e.getKey());
            } catch (Exception ex) {
                log.warn("Could not seed salary for '{}': {}", e.getKey(), ex.getMessage());
            }
        }
    }
}
