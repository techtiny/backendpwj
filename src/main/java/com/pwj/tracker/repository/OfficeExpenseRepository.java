package com.pwj.tracker.repository;

import com.pwj.tracker.model.OfficeExpense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OfficeExpenseRepository extends JpaRepository<OfficeExpense, Long> {
    List<OfficeExpense> findByPeriodMonthOrderByKindDescIdAsc(String periodMonth);
    List<OfficeExpense> findAllByOrderByPeriodMonthDescKindDescIdAsc();
    List<OfficeExpense> findByStatusOrderByPeriodMonthDescKindDescIdAsc(String status);
    boolean existsByKindAndStaffIdAndPeriodMonth(String kind, Long staffId, String periodMonth);
}
