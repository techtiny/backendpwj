package com.pwj.tracker.repository;

import com.pwj.tracker.model.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    List<Holiday> findAllByOrderByDateAsc();

    List<Holiday> findByDateBetweenOrderByDateAsc(LocalDate start, LocalDate end);

    boolean existsByDate(LocalDate date);
}
