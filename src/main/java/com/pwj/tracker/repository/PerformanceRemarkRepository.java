package com.pwj.tracker.repository;

import com.pwj.tracker.model.PerformanceRemark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PerformanceRemarkRepository extends JpaRepository<PerformanceRemark, Long> {

    List<PerformanceRemark> findByEmployeeIdOrderByRemarkDateDescIdDesc(Long employeeId);

    List<PerformanceRemark> findByRemarkDateOrderByIdDesc(java.time.LocalDate remarkDate);

    List<PerformanceRemark> findByAppealStatusOrderByAppealedAtDesc(String appealStatus);

    /** Unrated comments (since the review flow started) whose appeal stage is over: accepted by the employee, appeal rejected, or 48h lapsed. */
    @org.springframework.data.jpa.repository.Query("SELECT r FROM PerformanceRemark r WHERE r.rating IS NULL AND r.createdAt >= :since AND (" +
            "r.appealStatus = 'REJECTED' OR (r.appealedAt IS NULL AND (r.acknowledgedAt IS NOT NULL OR r.createdAt < :before))) ORDER BY r.createdAt DESC")
    List<PerformanceRemark> findAwaitingRating(@org.springframework.data.repository.query.Param("before") java.time.LocalDateTime before,
                                               @org.springframework.data.repository.query.Param("since") java.time.LocalDateTime since);
}
