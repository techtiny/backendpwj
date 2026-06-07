package com.pwj.tracker.account.repository;

import com.pwj.tracker.account.entity.FundTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;

@Repository
public interface FundTransferRepository extends JpaRepository<FundTransfer, Long> {

    List<FundTransfer> findAllByOrderByTransferDateDescIdDesc();

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM FundTransfer t WHERE t.fromProjectId = :projectId")
    BigDecimal sumOutgoing(Long projectId);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM FundTransfer t WHERE t.toProjectId = :projectId")
    BigDecimal sumIncoming(Long projectId);
}
