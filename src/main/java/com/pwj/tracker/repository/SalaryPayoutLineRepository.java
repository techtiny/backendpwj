package com.pwj.tracker.repository;

import com.pwj.tracker.model.SalaryPayoutLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalaryPayoutLineRepository extends JpaRepository<SalaryPayoutLine, Long> {

    List<SalaryPayoutLine> findByYearAndMonthOrderByFullNameAsc(Integer year, Integer month);

    boolean existsByYearAndMonth(Integer year, Integer month);
}
