package com.pwj.tracker.account.repository;

import com.pwj.tracker.account.entity.ProjectCollection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface ProjectCollectionRepository extends JpaRepository<ProjectCollection, Long> {

    List<ProjectCollection> findByProjectIdOrderByStageAsc(Long projectId);

    @Query("SELECT c FROM ProjectCollection c WHERE c.alertActive = true AND c.paymentDate IS NOT NULL AND c.paymentDate >= :today")
    List<ProjectCollection> findActiveAlerts(@Param("today") LocalDate today);

    @Query("SELECT c FROM ProjectCollection c WHERE c.paymentDate IS NOT NULL")
    List<ProjectCollection> findAllWithPaymentDate();

    @Query("SELECT COALESCE(SUM(c.collectedAmt), 0) FROM ProjectCollection c WHERE c.projectId = :projectId")
    BigDecimal sumCollectedByProject(@Param("projectId") Long projectId);

    @Query("SELECT c.projectId, COALESCE(SUM(c.collectedAmt), 0) FROM ProjectCollection c GROUP BY c.projectId")
    List<Object[]> sumCollectedGroupedByProject();
}
