package com.pwj.tracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Master HR record for every employee Happizo has ever had — active or long exited, with or
 *  without an app login. Distinct from AppUser (login accounts): AppUser only exists for
 *  people who use the app, while this covers the full historical roster from the company's
 *  master data sheet. linkedUsername connects a row to its AppUser when one exists, which is
 *  what powers the live PR/attendance stats and photo/performance features on the profile. */
@Entity
@Table(name = "hr_employee_master")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The "S No" column from the master sheet (e.g. "0 34") — stable natural key used to
     *  de-duplicate on re-import; not a display serial number. */
    @Column(name = "sheet_no", unique = true, length = 20)
    private String sheetNo;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "dob")
    private LocalDate dob;

    @Column(name = "gender", length = 10)
    private String gender;

    @Column(name = "department", length = 100)
    private String department;

    @Column(name = "designation", length = 150)
    private String designation;

    @Column(name = "doj")
    private LocalDate doj;

    /** Raw text from the sheet when doj couldn't be parsed into a real date (bad/partial entry). */
    @Column(name = "doj_raw", length = 50)
    private String dojRaw;

    @Column(name = "person_code", length = 30)
    private String personCode;

    /** Date of Relieving — null means still active. */
    @Column(name = "dor")
    private LocalDate dor;

    @Column(name = "dor_raw", length = 50)
    private String dorRaw;

    @Column(name = "designation_at_exit", length = 150)
    private String designationAtExit;

    /** Rejoin date, for employees who left and came back. */
    @Column(name = "doj2")
    private LocalDate doj2;

    @Column(name = "current_designation", length = 150)
    private String currentDesignation;

    /** Free-text remarks from the master sheet (exit notes etc.) — separate from the day-to-day
     *  performance remarks in PerformanceRemark. */
    @Column(name = "sheet_remarks", length = 1000)
    private String sheetRemarks;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    /** Links to AppUser.username when this person has an app login — null for historical/
     *  never-logged-in employees. Powers PR-raised and attendance stats on the profile. */
    @Column(name = "linked_username", length = 100)
    private String linkedUsername;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
