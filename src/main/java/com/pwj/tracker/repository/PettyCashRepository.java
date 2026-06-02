package com.pwj.tracker.repository;

import com.pwj.tracker.model.PettyCash;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface PettyCashRepository extends JpaRepository<PettyCash, Long> {

    List<PettyCash> findByUsernameOrderByExpenseDateDescCreatedAtDesc(String username);

    List<PettyCash> findByStatusOrderByCreatedAtDesc(String status);

    List<PettyCash> findAllByOrderByExpenseDateDescCreatedAtDesc();

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PettyCash p WHERE p.username = :username AND p.status = :status")
    BigDecimal sumByUsernameAndStatus(String username, String status);

    @Query("SELECT COUNT(p) FROM PettyCash p WHERE p.username = :username AND p.status = :status")
    long countByUsernameAndStatus(String username, String status);
}
