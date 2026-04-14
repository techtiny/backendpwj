package com.pwj.tracker.repository;

import com.pwj.tracker.model.PwjEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
    """)
    Page<PwjEntry> findFiltered(
        @Param("search")      String search,
        @Param("status")      PwjEntry.EntryStatus status,
        @Param("approval")    PwjEntry.ApprovalStatus approval,
        @Param("projectName") String projectName,
        @Param("raisedBy")    String raisedBy,
        Pageable pageable
    );

    long countByStatus(PwjEntry.EntryStatus status);
    long countByApprovalStatus(PwjEntry.ApprovalStatus approvalStatus);

    @Query("SELECT DISTINCT e.projectName FROM PwjEntry e ORDER BY e.projectName")
    List<String> findDistinctProjectNames();

    List<PwjEntry> findByApprovalStatusInAndStatus(
            List<PwjEntry.ApprovalStatus> statuses,
            PwjEntry.EntryStatus status
    );

    List<PwjEntry> findByDocStatus(PwjEntry.DocStatus docStatus);
}
