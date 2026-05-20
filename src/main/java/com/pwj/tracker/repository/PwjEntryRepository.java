package com.pwj.tracker.repository;

import com.pwj.tracker.model.PwjEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PwjEntryRepository extends JpaRepository<PwjEntry, Long> {

    @Query("""
        SELECT e FROM PwjEntry e
        WHERE (:search IS NULL OR :search = '' OR
               LOWER(e.materialRequired) LIKE LOWER(CONCAT('%', :search, '%')) OR
               LOWER(e.projectName)      LIKE LOWER(CONCAT('%', :search, '%')) OR
               LOWER(e.raisedBy)         LIKE LOWER(CONCAT('%', :search, '%')) OR
               LOWER(e.vendor)           LIKE LOWER(CONCAT('%', :search, '%')) OR
               LOWER(e.boqNo)            LIKE LOWER(CONCAT('%', :search, '%')))
        AND (:status      IS NULL OR e.status         = :status)
        AND (:approval    IS NULL OR e.approvalStatus = :approval)
        AND (:projectName IS NULL OR :projectName = '' OR LOWER(e.projectName) = LOWER(:projectName))
        AND (:raisedBy    IS NULL OR :raisedBy    = '' OR LOWER(e.raisedBy)    = LOWER(:raisedBy))
        AND (:dateFrom    IS NULL OR e.timestamp  >= :dateFrom)
        AND (:dateTo      IS NULL OR e.timestamp  <= :dateTo)
    """)
    Page<PwjEntry> findFiltered(
        @Param("search")      String search,
        @Param("status")      PwjEntry.EntryStatus status,
        @Param("approval")    PwjEntry.ApprovalStatus approval,
        @Param("projectName") String projectName,
        @Param("raisedBy")    String raisedBy,
        @Param("dateFrom")    LocalDateTime dateFrom,
        @Param("dateTo")      LocalDateTime dateTo,
        Pageable pageable
    );

    long countByStatus(PwjEntry.EntryStatus status);
    long countByApprovalStatus(PwjEntry.ApprovalStatus approvalStatus);

    @Query("SELECT COUNT(e) FROM PwjEntry e WHERE e.status = :status AND LOWER(e.raisedBy) = LOWER(:raisedBy)")
    long countByStatusAndRaisedBy(@Param("status") PwjEntry.EntryStatus status, @Param("raisedBy") String raisedBy);

    @Query("SELECT COUNT(e) FROM PwjEntry e WHERE e.approvalStatus = :approval AND LOWER(e.raisedBy) = LOWER(:raisedBy)")
    long countByApprovalStatusAndRaisedBy(@Param("approval") PwjEntry.ApprovalStatus approval, @Param("raisedBy") String raisedBy);

    @Query("SELECT DISTINCT e.projectName FROM PwjEntry e ORDER BY e.projectName")
    List<String> findDistinctProjectNames();

    List<PwjEntry> findByApprovalStatusInAndStatus(
            List<PwjEntry.ApprovalStatus> statuses,
            PwjEntry.EntryStatus status
    );

    List<PwjEntry> findByDocStatus(PwjEntry.DocStatus docStatus);

    @Query("SELECT e FROM PwjEntry e WHERE e.docNumber IS NOT NULL AND e.docData IS NOT NULL ORDER BY e.updatedAt DESC")
    List<PwjEntry> findAllWithDocData();

    @Modifying
    @Query("UPDATE PwjEntry e SET e.dependency = 'OH Approval' WHERE e.dependency IS NULL OR e.dependency = ''")
    int backfillNullDependency();
}
