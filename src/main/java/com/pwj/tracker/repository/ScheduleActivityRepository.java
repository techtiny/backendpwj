package com.pwj.tracker.repository;

import com.pwj.tracker.model.ScheduleActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScheduleActivityRepository extends JpaRepository<ScheduleActivity, Long> {

    List<ScheduleActivity> findByProjectIdOrderBySortOrderAscIdAsc(Long projectId);

    void deleteByProjectId(Long projectId);
}
