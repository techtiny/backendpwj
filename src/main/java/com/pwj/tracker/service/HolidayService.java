package com.pwj.tracker.service;

import com.pwj.tracker.model.Holiday;
import com.pwj.tracker.repository.HolidayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HolidayService {

    private final HolidayRepository repo;

    public List<Holiday> list() {
        return repo.findAllByOrderByDateAsc();
    }

    /** The set of holiday dates falling inside [start, end] — used to exclude them from LOP / attendance. */
    public Set<LocalDate> datesBetween(LocalDate start, LocalDate end) {
        return repo.findByDateBetweenOrderByDateAsc(start, end).stream()
                .map(Holiday::getDate).collect(Collectors.toSet());
    }

    @Transactional
    public Holiday add(LocalDate date, String occasion, String actionBy) {
        if (date == null) throw new RuntimeException("Date is required");
        if (occasion == null || occasion.isBlank()) throw new RuntimeException("Occasion is required");
        if (repo.existsByDate(date)) throw new RuntimeException("A holiday is already declared on " + date);
        return repo.save(Holiday.builder().date(date).occasion(occasion.trim()).createdBy(actionBy).build());
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) throw new RuntimeException("Holiday not found: " + id);
        repo.deleteById(id);
    }
}
