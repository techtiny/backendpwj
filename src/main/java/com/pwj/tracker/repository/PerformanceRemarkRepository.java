package com.pwj.tracker.repository;

import com.pwj.tracker.model.PerformanceRemark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PerformanceRemarkRepository extends JpaRepository<PerformanceRemark, Long> {

    List<PerformanceRemark> findByEmployeeIdOrderByRemarkDateDescIdDesc(Long employeeId);
}
