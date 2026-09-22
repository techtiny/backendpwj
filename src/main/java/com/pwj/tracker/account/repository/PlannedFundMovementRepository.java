package com.pwj.tracker.account.repository;

import com.pwj.tracker.account.entity.PlannedFundMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlannedFundMovementRepository extends JpaRepository<PlannedFundMovement, Long> {

    List<PlannedFundMovement> findByDirectionOrderByMovementDateDescIdDesc(String direction);

    List<PlannedFundMovement> findAllByOrderByMovementDateDescIdDesc();
}
