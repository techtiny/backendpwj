package com.pwj.tracker.repository;

import com.pwj.tracker.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/*Module for Project repository */

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByActiveTrueOrderByNameAsc();
    List<Project> findAllByOrderByNameAsc();
    List<Project> findByActiveTrueAndEligibleForAccountsTrueOrderByNameAsc();
    Optional<Project> findByNameIgnoreCase(String name);
    String a = "Shobana";
}
