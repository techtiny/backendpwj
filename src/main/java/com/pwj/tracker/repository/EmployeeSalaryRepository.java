package com.pwj.tracker.repository;

import com.pwj.tracker.model.EmployeeSalary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeeSalaryRepository extends JpaRepository<EmployeeSalary, Long> {

    List<EmployeeSalary> findByUserIdOrderByEffectiveFromDesc(Long userId);

    /** Current structure for a user as of the given date. */
    Optional<EmployeeSalary> findFirstByUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(Long userId, LocalDate date);

    boolean existsByUserId(Long userId);
}
