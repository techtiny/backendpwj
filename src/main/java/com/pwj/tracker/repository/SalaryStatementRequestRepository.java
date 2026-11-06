package com.pwj.tracker.repository;

import com.pwj.tracker.model.SalaryStatementRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SalaryStatementRequestRepository extends JpaRepository<SalaryStatementRequest, Long> {

    List<SalaryStatementRequest> findByUsernameOrderByCreatedAtDesc(String username);

    List<SalaryStatementRequest> findByStatusOrderByCreatedAtDesc(String status);

    List<SalaryStatementRequest> findAllByOrderByCreatedAtDesc();

    Optional<SalaryStatementRequest> findFirstByUsernameAndYearAndMonthAndStatusIn(String username, Integer year, Integer month, List<String> statuses);
}
