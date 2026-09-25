package com.pwj.tracker.repository;

import com.pwj.tracker.model.PerformanceRemark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PerformanceRemarkRepository extends JpaRepository<PerformanceRemark, Long> {

    List<PerformanceRemark> findByEmployeeIdOrderByRemarkDateDesc(Long employeeId);

    Optional<PerformanceRemark> findByEmployeeIdAndRemarkDate(Long employeeId, LocalDate remarkDate);
}
