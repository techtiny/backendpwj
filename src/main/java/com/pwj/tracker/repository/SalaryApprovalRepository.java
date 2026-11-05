package com.pwj.tracker.repository;

import com.pwj.tracker.model.SalaryApproval;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SalaryApprovalRepository extends JpaRepository<SalaryApproval, Long> {

    Optional<SalaryApproval> findByYearAndMonth(Integer year, Integer month);

    List<SalaryApproval> findByReleasedTrue();

    List<SalaryApproval> findByReleasedFalse();

    List<SalaryApproval> findByPayoutStatusIsNotNull();
}
