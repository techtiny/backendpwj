package com.pwj.tracker.repository;

import com.pwj.tracker.model.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    Optional<Attendance> findByUsernameAndWorkDate(String username, LocalDate workDate);

    List<Attendance> findByUsernameOrderByWorkDateDesc(String username);

    List<Attendance> findByWorkDateOrderByFullNameAsc(LocalDate workDate);

    List<Attendance> findAllByOrderByWorkDateDescCheckInTimeDesc();

    @Query("SELECT a FROM Attendance a WHERE a.workDate BETWEEN :from AND :to ORDER BY a.workDate DESC, a.fullName ASC")
    List<Attendance> findByDateRange(LocalDate from, LocalDate to);

    @Query("SELECT COUNT(a) FROM Attendance a WHERE a.username = :username AND a.status = :status AND a.workDate >= :from")
    long countByUsernameAndStatusSince(String username, String status, LocalDate from);

    @Query("SELECT COUNT(a) FROM Attendance a WHERE a.username = :username AND a.workDate >= :from")
    long countByUsernameSince(String username, LocalDate from);

    List<Attendance> findByUsernameInOrderByWorkDateDescCheckInTimeDesc(List<String> usernames);

    /** Records from past days where employee checked in but never checked out */
    @Query("SELECT a FROM Attendance a WHERE a.checkOutTime IS NULL AND a.workDate < :today ORDER BY a.workDate DESC, a.fullName ASC")
    List<Attendance> findIncompleteBeforeDate(@Param("today") LocalDate today);
}
