package com.pwj.tracker.config;

import com.pwj.tracker.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** The person code (Employee Master) is the single employee identifier in the portal. On every boot, make each
 *  linked login's employee number equal to its person code (generating a code for a linked employee that has none),
 *  and refresh the employee number copied onto salary structure rows. Idempotent. */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class PersonCodeSyncRunner implements ApplicationRunner {

    private final EmployeeService employeeService;
    private final com.pwj.tracker.service.SalaryAdvanceService advanceService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int changed = employeeService.syncAllPersonCodes();
            if (changed > 0) log.info("Employee number set to person code for {} logins", changed);
            jdbcTemplate.update("UPDATE hr_employee_salary s JOIN app_user au ON au.id = s.user_id " +
                    "SET s.employee_number = au.employee_number WHERE au.employee_number IS NOT NULL " +
                    "AND (s.employee_number IS NULL OR s.employee_number <> au.employee_number)");
        } catch (Exception e) {
            log.warn("Could not sync employee numbers to person codes: {}", e.getMessage());
        }
        try {
            int moved = advanceService.applyStartRuleToLegacyAdvances();
            if (moved > 0) log.info("Moved {} paid advance(s) to the payroll month given by the 7th-of-the-month rule", moved);
        } catch (Exception e) {
            log.warn("Could not apply the advance start-month rule: {}", e.getMessage());
        }
    }
}
