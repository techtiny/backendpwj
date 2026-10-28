package com.pwj.tracker.repository;

import com.pwj.tracker.model.ScheduleRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScheduleRevisionRepository extends JpaRepository<ScheduleRevision, Long> {

    List<ScheduleRevision> findByProjectIdOrderByRevisionNoDesc(Long projectId);

    Optional<ScheduleRevision> findByProjectIdAndRevisionNo(Long projectId, Integer revisionNo);

    void deleteByProjectId(Long projectId);
}
