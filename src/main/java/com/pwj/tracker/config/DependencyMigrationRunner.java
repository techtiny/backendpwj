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

        // Step 9: seed the 2026 company holiday list (idempotent — skips dates already present).
        java.util.Map<String, String> holidays2026 = new java.util.LinkedHashMap<>();
        holidays2026.put("2026-01-01", "New Year");
        holidays2026.put("2026-01-15", "Pongal");
        holidays2026.put("2026-01-16", "Thiruvalluvar Day");
        holidays2026.put("2026-01-26", "Republic Day");
        holidays2026.put("2026-04-14", "Tamil New Year");
        holidays2026.put("2026-05-01", "Labour Day");
        holidays2026.put("2026-08-15", "Independence Day");
        holidays2026.put("2026-09-14", "Ganesh Chathurthi");
        holidays2026.put("2026-10-02", "Gandhi Jayanthi");
        holidays2026.put("2026-10-20", "Ayudha Pooja");
        holidays2026.put("2026-11-07", "Diwali");
        for (java.util.Map.Entry<String, String> e : holidays2026.entrySet()) {
            try {
                int rows = jdbcTemplate.update(
                    "INSERT INTO hr_holiday (date, occasion, created_by, created_at) " +
                    "SELECT ?, ?, 'system', NOW() WHERE NOT EXISTS (SELECT 1 FROM hr_holiday WHERE date = ?)",
                    e.getKey(), e.getValue(), e.getKey());
                if (rows > 0) log.info("Seeded holiday {} — {}", e.getKey(), e.getValue());
            } catch (Exception ex) {
                log.warn("Could not seed holiday {}: {}", e.getKey(), ex.getMessage());
            }
        }
        // Step 10: seed the full Employee Master roster from the company's master data sheet
        // (54 people, 2017-present, active + historical/exited) — idempotent, keyed by sheet_no.
            seedEmployee(jdbcTemplate, "00 1", "Sunil Vassan S B", "1989-08-04", "M", "Management", "Chief Executive Officer", "2017-01-01", null, "H-17-0001", null, null, null, null, null, null);
            seedEmployee(jdbcTemplate, "00 2", "Anusha P K B", "1990-05-11", "F", "Management", "Vice President", "2017-01-01", null, "H-17-0002", null, null, null, null, null, null);
            seedEmployee(jdbcTemplate, "00 3", "R V Subbaiah", "1970-02-10", "M", "Admin", "General Manager", "2017-01-01", null, "H-17-0003", null, null, "General Manager", null, null, null);
            seedEmployee(jdbcTemplate, "00 4", "Hemapriya P", "1994-02-24", "F", "Admin", "Admin Executive", "2026-09-17", null, "H-17-0004", "2022-09-13", null, null, null, null, null);
            seedEmployee(jdbcTemplate, "00 5", "Kannan M", "1973-06-08", "M", "Admin", "Office runner", "2018-01-01", null, "H-18-0005", "2025-10-01", null, "Office runner", null, null, "he exited 4months in middle");
            seedEmployee(jdbcTemplate, "00 6", "Kumari", "1981-04-10", "F", "Accounts", "Accounts Executive", null, null, null, null, null, null, null, null, null);
            seedEmployee(jdbcTemplate, "00 7", "Nisha", null, "F", "Accounts", "Accounts Asst.", null, null, null, null, null, null, null, null, null);
            seedEmployee(jdbcTemplate, "00 8", "Mhd Azarudeen", "1994-05-24", "M", "Projects", "Site Engineer", null, null, null, null, null, "Site Engineer", null, null, null);
            seedEmployee(jdbcTemplate, "00 9", "Ragapriya L", null, "F", "Admin", "Admin Asst.", null, null, null, null, null, "Admin Asst.", null, null, null);
            seedEmployee(jdbcTemplate, "0 10", "Mhd Haji", "1994-05-04", "M", "Projects", "Site Engineer", null, null, null, null, null, "Site Engineer", null, null, null);
            seedEmployee(jdbcTemplate, "0 11", "Bhaskaran A", "1991-09-09", "M", "Projects", "Site Engineer", "2019-06-01", null, "H-25-0011", "2020-09-26", null, "Site Engineer", "2025-02-12", "Costing & Planning Engineer", null);
            seedEmployee(jdbcTemplate, "0 12", "Sathish S", "1993-10-01", "M", "Projects", "Site Engineer", "2019-01-01", null, null, null, null, null, null, null, null);
            seedEmployee(jdbcTemplate, "0 13", "Gopi P", "1995-05-06", "M", "Projects", "Site Engineer", "2019-11-24", null, null, "2025-06-12", null, "Sr.Site Engineer", null, null, null);
            seedEmployee(jdbcTemplate, "0 14", "R S Bharath", "1988-01-31", "M", "Management", "Operations Head", null, "12.2021", "H-21-0014", null, null, null, null, null, null);
            seedEmployee(jdbcTemplate, "0 15", "Gurunathan", null, "M", "Projects", "Site Engineer", "2026-07-21", null, null, "2022-12-10", null, "Site Engineer", null, null, null);
            seedEmployee(jdbcTemplate, "0 16", "Sakthi Ganesh", "1996-06-05", "M", "Projects", "Site Engineer", "2022-01-03", null, "H-22-0016", null, null, null, null, null, null);
            seedEmployee(jdbcTemplate, "0 17", "Gowtham", "1995-12-29", "M", "Projects", "Site Engineer", "2022-02-08", null, null, null, null, "Site Engineer", null, null, "exit was not smooth");
            seedEmployee(jdbcTemplate, "0 18", "Maheshwar M", "1996-09-11", "M", "Procurement", "Procurement Executive", "2022-02-21", null, null, "2024-12-03", null, "Procurement Executive", null, null, null);
            seedEmployee(jdbcTemplate, "0 19", "Indira", null, "F", "Accounts", "Accounts Executive", "2022-09-09", null, null, null, null, null, null, null, null);
            seedEmployee(jdbcTemplate, "0 20", "Tamil Selvi", "1990-03-05", "F", "Procurement", "Procurement Asst.", null, null, null, null, null, null, null, null, "unprofessional exit");
            seedEmployee(jdbcTemplate, "0 21", "Jana", "1999-11-14", "M", "Projects", "Jr.Site Engineer", "2023-03-03", null, null, "2023-08-05", null, "Site Engineer", null, null, null);
            seedEmployee(jdbcTemplate, "0 22", "Yogesh P", "1997-12-29", "M", "Projects", "Site Engineer", "2023-03-06", null, null, "2026-02-09", null, "Site Engineer", null, null, null);
            seedEmployee(jdbcTemplate, "0 23", "Thenis Mary", "1993-05-02", "F", "Admin", "Admin Acc Executive", "2023-05-15", null, "H-23-0023", "2024-07-25", null, null, null, null, null);
            seedEmployee(jdbcTemplate, "0 24", "Venkatesan", "1997-10-11", "M", "Projects", "Site Engineer", "2023-06-28", null, null, "2026-01-03", null, "Site Engineer", null, null, null);
            seedEmployee(jdbcTemplate, "0 25", "Pugalmathi", "1995-05-09", "F", "Accounts", "Accounts Executive", "2023-07-10", null, null, null, null, null, null, null, null);
            seedEmployee(jdbcTemplate, "0 26", "Monisha M", "1998-08-03", "F", "Procurement", "Procurement Asst.", "2023-07-10", null, null, null, null, null, null, null, null);
            seedEmployee(jdbcTemplate, "0 27", "Sugantha", "1994-12-01", "F", "Accounts", "Accounts Executive", "2023-11-30", null, null, "2024-04-25", null, "Accounts Executive", null, null, null);
            seedEmployee(jdbcTemplate, "0 28", "Balachander", "1998-03-22", "M", "Projects", "Site Engineer", "2023-12-08", null, null, null, null, null, null, null, null);
            seedEmployee(jdbcTemplate, "0 29", "Sathishkumar", null, "M", "Projects", "Site Engineer", null, null, null, null, null, null, null, null, null);
            seedEmployee(jdbcTemplate, "0 30", "Kejalakshmi", "2001-05-26", "F", "Accounts", "Accounts Executive", "2024-01-01", null, null, "2025-03-01", null, "Accounts Executive", null, null, null);
            seedEmployee(jdbcTemplate, "0 31", "Kalaicholan", "1993-11-20", "M", "Projects", "Site Engineer", "2024-01-03", null, null, "2025-10-16", null, "Site Engineer", null, null, null);
            seedEmployee(jdbcTemplate, "0 32", "Arun Kumar", "1996-07-31", "M", "Projects", "Site Engineer", "2024-01-23", null, null, null, null, null, null, null, null);
            seedEmployee(jdbcTemplate, "0 33", "Chandrasekar", "1992-07-19", "M", "Projects", "Site Engineer", "2024-06-18", null, null, "2025-10-18", null, "Site Engineer", null, null, null);
            seedEmployee(jdbcTemplate, "0 34", "Jagan", "1994-07-19", "M", "Projects", "Site Engineer", "2024-07-01", null, "H-24-0034", null, null, null, null, null, null);
            seedEmployee(jdbcTemplate, "0 35", "Sathishkumar vasudevan", null, "M", "Projects", "Site Supervisor", "2024-08-14", null, null, null, null, null, null, null, "absconded");
            seedEmployee(jdbcTemplate, "0 36", "Ayyanar", null, "M", "Procurement", "Procurement Asst.", "2024-09-11", null, null, null, null, null, null, null, "absconded, sim had to be retreived");
            seedEmployee(jdbcTemplate, "0 37", "Javidh", "2001-05-07", "M", "Procurement", "Procurement Asst.", "2024-09-23", null, null, "2025-01-01", null, "Procurement Asst.", null, null, null);
            seedEmployee(jdbcTemplate, "0 38", "Anandha Aakash", "2001-03-29", "M", "Procurement", "Procurement Engineer", "2024-11-18", null, "H-24-0038", null, null, null, null, null, null);
            seedEmployee(jdbcTemplate, "0 39", "Vidhya P", "2001-09-18", "F", "Admin", "Admin Asst.", "2024-12-13", null, "H-24-0039", null, null, null, null, null, null);
            seedEmployee(jdbcTemplate, "0 40", "Sripathy", "1972-10-14", "M", "Accounts", "Accounts Executive", "2025-02-03", null, null, "2025-07-25", null, "Accounts Executive", null, null, "unprofessional exit");
            seedEmployee(jdbcTemplate, "0 41", "Rajesh", null, "M", "Projects", "Site Engineer", "2025-03-11", null, null, "2023-01-10", null, "Site Engineer", null, null, "smooth exit");
            seedEmployee(jdbcTemplate, "0 42", "Sudhasurya", "1998-08-12", "M", "Projects", "Site Engineer", "2025-05-23", null, null, "2025-08-23", null, "Site Engineer", null, null, null);
            seedEmployee(jdbcTemplate, "0 43", "Saravanan", "1995-05-04", "M", "Projects", "Project Engn", "2025-05-23", null, null, "2025-11-28", null, "Project Engn", null, null, "unprofessional exit");
            seedEmployee(jdbcTemplate, "0 44", "Mohd Azeem", "1999-04-24", "M", "Projects", "Safety Engineer", "2025-12-28", null, null, "2025-03-11", null, "Safety Engineer", null, null, null);
            seedEmployee(jdbcTemplate, "0 45", "Jayakumar", "1987-03-17", "M", "Projects", "Project Manager", "2026-02-12", null, "H-26-0045", null, null, null, null, null, null);
            seedEmployee(jdbcTemplate, "0 46", "Santhosh Kumar", "1995-09-26", "M", "Projects", "Site Engineer", "2026-02-06", null, "H-26-0046", null, null, null, null, null, null);
            seedEmployee(jdbcTemplate, "0 47", "Balaji", "1997-06-13", "M", "Projects", "Senior Site Engineer", "2026-04-20", null, "H-26-0047", null, null, null, null, null, null);
            seedEmployee(jdbcTemplate, "0 48", "Aravind Kumar", "1995-10-22", "M", "Projects", "Site Engineer", "2026-05-25", null, "H-26-0048", null, null, null, null, null, null);
            seedEmployee(jdbcTemplate, "0 49", "Keerthi", "2002-01-27", "M", "Projects", "Site Supervisor", "2026-05-18", null, "H-26-0049", null, null, null, null, null, null);
            seedEmployee(jdbcTemplate, "0 50", "Sakthivel", "2006-10-03", "M", "Projects", "Site Supervisor", "2026-05-28", null, "H-26-0050", null, null, null, null, null, null);
            seedEmployee(jdbcTemplate, "0 51", "Sandilyan", "1997-06-24", "M", "Projects", "Site Engineer", "2026-06-10", null, "H-26-0051", null, null, null, null, null, null);
            seedEmployee(jdbcTemplate, "0 52", "Suresh", "2005-04-10", "M", "Procurement", "Procurement Executive", "2026-06-29", null, "H-26-0052", null, null, null, null, null, null);
            seedEmployee(jdbcTemplate, "0 53", "Kishore", "1997-01-12", "M", "Projects", "Assistant Project Manager", "2026-07-10", null, "H-26-0053", null, null, null, null, null, null);
            seedEmployee(jdbcTemplate, "0 54", "Divson", "1999-11-15", "M", "Projects", "Site Engineer", "2026-07-09", null, "H-26-0054", null, null, null, null, null, null);

        // Step 11: auto-link Employee Master rows to app logins by exact full-name match —
        // anything that doesn't match exactly (nicknames, spelling drift) can be linked manually
        // from the Employees page. Safe to run every startup: only fills in rows still unlinked.
        try {
            int linked = jdbcTemplate.update(
                "UPDATE hr_employee_master m " +
                "JOIN app_user au ON LOWER(TRIM(au.full_name)) = LOWER(TRIM(m.full_name)) " +
                "SET m.linked_username = au.username " +
                "WHERE m.linked_username IS NULL");
            if (linked > 0) log.info("Auto-linked {} employee master rows to app logins", linked);
        } catch (Exception ex) {
            log.warn("Could not auto-link employee master rows: {}", ex.getMessage());
        }
    }

    /** Idempotent single-row insert for the Employee Master seed — skips if sheet_no already exists. */
    private void seedEmployee(JdbcTemplate jdbc, String sheetNo, String fullName, String dob, String gender,
                               String dept, String designation, String doj, String dojRaw, String personCode,
                               String dor, String dorRaw, String designationAtExit, String doj2,
                               String currentDesignation, String remarks) {
        try {
            int rows = jdbc.update(
                "INSERT INTO hr_employee_master " +
                "(sheet_no, full_name, dob, gender, department, designation, doj, doj_raw, person_code, " +
                " dor, dor_raw, designation_at_exit, doj2, current_designation, sheet_remarks, created_at) " +
                "SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW() " +
                "WHERE NOT EXISTS (SELECT 1 FROM hr_employee_master WHERE sheet_no = ?)",
                sheetNo, fullName, dob, gender, dept, designation, doj, dojRaw, personCode,
                dor, dorRaw, designationAtExit, doj2, currentDesignation, remarks, sheetNo);
            if (rows > 0) log.info("Seeded employee master row: {} ({})", fullName, sheetNo);
        } catch (Exception ex) {
            log.warn("Could not seed employee master row '{}': {}", fullName, ex.getMessage());
        }
    }
}
