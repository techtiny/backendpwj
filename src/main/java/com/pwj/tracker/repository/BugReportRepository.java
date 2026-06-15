package com.pwj.tracker.repository;

import com.pwj.tracker.model.BugReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BugReportRepository extends JpaRepository<BugReport, Long> {

    List<BugReport> findAllByOrderByCreatedAtDesc();

    @Query("SELECT b FROM BugReport b WHERE " +
           "(:status IS NULL OR b.status = :status) AND " +
           "(:severity IS NULL OR b.severity = :severity) AND " +
           "(:module IS NULL OR b.module = :module) AND " +
           "(:assignedTo IS NULL OR b.assignedTo = :assignedTo) AND " +
           "(:search IS NULL OR LOWER(b.title) LIKE :search OR LOWER(b.description) LIKE :search) " +
           "ORDER BY b.createdAt DESC")
    List<BugReport> findWithFilters(@Param("status") String status,
                                     @Param("severity") String severity,
                                     @Param("module") String module,
                                     @Param("assignedTo") String assignedTo,
                                     @Param("search") String search);
}
