package com.pwj.tracker.repository;

import com.pwj.tracker.model.ProjectSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProjectScheduleRepository extends JpaRepository<ProjectSchedule, Long> {

    Optional<ProjectSchedule> findByProjectId(Long projectId);

    void deleteByProjectId(Long projectId);
}
