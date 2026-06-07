package com.pwj.tracker.account.repository;

import com.pwj.tracker.account.entity.SalesLead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;

@Repository
public interface SalesLeadRepository extends JpaRepository<SalesLead, Long> {

    List<SalesLead> findAllByOrderByCreatedAtDesc();

    long countByStage(String stage);

    @Query("SELECT COALESCE(SUM(s.dealValue), 0) FROM SalesLead s WHERE s.stage = :stage")
    BigDecimal sumDealValueByStage(String stage);

    @Query("SELECT COALESCE(SUM(s.dealValue), 0) FROM SalesLead s WHERE s.stage NOT IN ('WON', 'LOST')")
    BigDecimal sumPipelineValue();

    @Query("SELECT COALESCE(SUM(s.dealValue), 0) FROM SalesLead s WHERE s.stage = 'WON'")
    BigDecimal sumWonValue();
}
