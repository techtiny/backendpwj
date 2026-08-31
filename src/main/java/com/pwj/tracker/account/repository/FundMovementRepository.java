package com.pwj.tracker.account.repository;

import com.pwj.tracker.account.entity.FundMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FundMovementRepository extends JpaRepository<FundMovement, Long> {

    List<FundMovement> findByDirectionOrderByMovementDateDescIdDesc(String direction);

    List<FundMovement> findAllByOrderByMovementDateDescIdDesc();

    /** Rows of [projectId, direction, sum(amount)] for every project that has a movement. */
    @Query("SELECT m.projectId, m.direction, COALESCE(SUM(m.amount), 0) " +
           "FROM FundMovement m WHERE m.projectId IS NOT NULL GROUP BY m.projectId, m.direction")
    List<Object[]> sumGroupedByProjectAndDirection();
}
