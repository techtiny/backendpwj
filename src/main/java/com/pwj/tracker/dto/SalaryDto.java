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
        public BigDecimal pfAmount;   // optional override; default 12% of Basic
        public BigDecimal ptAmount;   // optional override; default 208
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
        public BigDecimal pfAmount;   // effective monthly PF
        public BigDecimal ptAmount;   // effective monthly PT
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

        public java.time.LocalDate cycleStart;  // 26th of the previous month
        public java.time.LocalDate cycleEnd;    // 25th of the selected month
        public int daysInMonth;                 // actual day count of the cycle above (28-31)
        public BigDecimal leaveDays;            // approved leave days in the cycle
        public int freeCasualLeave;             // 1
        public BigDecimal unauthorizedDays;     // Mon-Sat, no check-in, no leave, not a holiday — 1.5x straight to LOP, no free CL
        public java.util.List<java.time.LocalDate> unauthorizedDates; // those days — HR can regularize each as leave
        public BigDecimal lopDays;              // loss-of-pay days actually applied
        public BigDecimal extraWorkingDays;
        public BigDecimal workingDays;

        // Fixed (contracted) structure
        public BigDecimal fixedGross, fixedBasic, fixedHra, fixedOther, fixedTotalGross;
        public BigDecimal fixedPf, fixedPt, fixedTotalDed, fixedTakeHome, fixedEmployer, fixedCtc;
        // Actual (prorated for the month)
        public BigDecimal gross, basic, hra, otherAllowance, totalGross;
        public BigDecimal pf, pt, totalDed, takeHome, employer, ctc;
        public BigDecimal advanceDeduction;  // this month's advance instalment(s), already inside totalDed

        public String remarks;
        public boolean finalized;
    }

    /** HR marks an unauthorized (never-applied) day as approved leave on the employee's behalf. */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class RegularizeRequest {
        public java.time.LocalDate date;
        public String leaveType;   // CASUAL (default) | SICK | EARNED | OTHER
        public String reason;
        public String username;    // the Admin / VP doing it
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
