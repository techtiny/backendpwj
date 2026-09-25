package com.pwj.tracker.repository;

import com.pwj.tracker.model.PettyCash;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface PettyCashRepository extends JpaRepository<PettyCash, Long> {

    List<PettyCash> findByUsernameAndRequestTypeOrderByExpenseDateDescCreatedAtDesc(String username, String requestType);

    List<PettyCash> findByStatusAndRequestTypeOrderByCreatedAtDesc(String status, String requestType);

    List<PettyCash> findByRequestTypeOrderByExpenseDateDescCreatedAtDesc(String requestType);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PettyCash p WHERE p.username = :username AND p.status = :status AND p.requestType = :requestType")
    BigDecimal sumByUsernameAndStatus(String username, String status, String requestType);

    @Query("SELECT COUNT(p) FROM PettyCash p WHERE p.username = :username AND p.status = :status AND p.requestType = :requestType")
    long countByUsernameAndStatus(String username, String status, String requestType);

    boolean existsByUsernameAndProjectNameAndStatus(String username, String projectName, String status);

    /** Count of untallied (non-REJECTED, non-PROOF_VERIFIED) requests for a project, scoped to one request type; a 3rd new request is blocked once this reaches 2 */
    @Query("SELECT COUNT(p) FROM PettyCash p WHERE p.username = :username AND p.projectName = :projectName AND p.requestType = :requestType AND p.status NOT IN ('PROOF_VERIFIED', 'REJECTED')")
    long countActiveRequestsForProject(String username, String projectName, String requestType);

    /** All entries awaiting Admin proof review, scoped to one request type */
    @Query("SELECT p FROM PettyCash p WHERE p.status = 'PROOF_SUBMITTED' AND p.requestType = :requestType ORDER BY p.proofSubmittedAt ASC")
    List<PettyCash> findProofSubmittedEntries(String requestType);
}
