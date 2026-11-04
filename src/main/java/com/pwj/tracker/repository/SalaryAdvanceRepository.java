package com.pwj.tracker.repository;

import com.pwj.tracker.model.SalaryAdvance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalaryAdvanceRepository extends JpaRepository<SalaryAdvance, Long> {

    List<SalaryAdvance> findByUsernameOrderByCreatedAtDesc(String username);

    List<SalaryAdvance> findByStatusOrderByCreatedAtDesc(String status);

    List<SalaryAdvance> findAllByOrderByCreatedAtDesc();

    List<SalaryAdvance> findByUsernameAndStatus(String username, String status);

    boolean existsByUsernameAndStatusIn(String username, List<String> statuses);
}
