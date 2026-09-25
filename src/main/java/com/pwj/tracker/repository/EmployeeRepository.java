package com.pwj.tracker.repository;

import com.pwj.tracker.model.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    List<Employee> findAllByOrderByFullNameAsc();

    Optional<Employee> findBySheetNo(String sheetNo);

    Optional<Employee> findByLinkedUsername(String username);

    boolean existsBySheetNo(String sheetNo);
}
