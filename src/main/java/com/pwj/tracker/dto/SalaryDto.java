package com.pwj.tracker.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public class SalaryDto {

    /** Define / revise a salary structure. */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class StructureRequest {
        public Long userId;
        public BigDecimal monthlyGross;
        public Boolean pfApplicable;
        public Boolean ptApplicable;
        public LocalDate effectiveFrom;
        public String note;
        public String actionBy;
    }

    /** One employee's current structure (or the fact that they have none yet). */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class StructureView {
        public Long userId;
        public String employeeNumber;
        public String name;
        public String designation;
        public String role;
        public boolean hasSalary;
        public BigDecimal monthlyGross;
        public BigDecimal basic;
        public BigDecimal hra;
        public BigDecimal otherAllowance;
        public Boolean pfApplicable;
        public Boolean ptApplicable;
        public LocalDate effectiveFrom;
        public String note;
    }

    /** One row of the monthly salary sheet (mirrors the salary Excel columns). */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SheetRow {
        public Long userId;
        public String employeeNumber;
        public String name;
        public String designation;

        public int daysInMonth;                 // fixed 30
        public BigDecimal leaveDays;            // approved leave days in the month
        public int freeCasualLeave;             // 1
        public BigDecimal lopDays;              // loss-of-pay days actually applied
        public BigDecimal extraWorkingDays;
        public BigDecimal workingDays;

        // Fixed (contracted) structure
        public BigDecimal fixedGross, fixedBasic, fixedHra, fixedOther, fixedTotalGross;
        public BigDecimal fixedPf, fixedPt, fixedTotalDed, fixedTakeHome, fixedEmployer, fixedCtc;
        // Actual (prorated for the month)
        public BigDecimal gross, basic, hra, otherAllowance, totalGross;
        public BigDecimal pf, pt, totalDed, takeHome, employer, ctc;

        public String remarks;
        public boolean finalized;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class AdjustmentRequest {
        public BigDecimal extraWorkingDays;
        public BigDecimal manualLopDays;
        public BigDecimal manualWorkingDays;
        public String remarks;
        public Boolean finalized;
        public String actionBy;
    }
}
