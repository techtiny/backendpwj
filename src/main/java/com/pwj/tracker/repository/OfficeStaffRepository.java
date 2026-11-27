package com.pwj.tracker.repository;

import com.pwj.tracker.model.OfficeStaff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OfficeStaffRepository extends JpaRepository<OfficeStaff, Long> {
    List<OfficeStaff> findAllByOrderByIdAsc();
    List<OfficeStaff> findByActiveTrue();
}
