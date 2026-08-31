package com.pwj.tracker.account.repository;

import com.pwj.tracker.account.entity.FundMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FundMovementRepository extends JpaRepository<FundMovement, Long> {

    List<FundMovement> findByDirectionOrderByMovementDateDescIdDesc(String direction);

    List<FundMovement> findAllByOrderByMovementDateDescIdDesc();
}
