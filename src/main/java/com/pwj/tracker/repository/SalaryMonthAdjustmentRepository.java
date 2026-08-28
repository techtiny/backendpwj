package com.pwj.tracker.repository;

import com.pwj.tracker.model.SalaryMonthAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SalaryMonthAdjustmentRepository extends JpaRepository<SalaryMonthAdjustment, Long> {

    Optional<SalaryMonthAdjustment> findByUserIdAndYearAndMonth(Long userId, Integer year, Integer month);

    List<SalaryMonthAdjustment> findByYearAndMonth(Integer year, Integer month);
}
