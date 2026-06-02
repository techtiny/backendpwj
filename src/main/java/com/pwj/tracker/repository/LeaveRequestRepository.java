package com.pwj.tracker.repository;

import com.pwj.tracker.model.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    List<LeaveRequest> findByUsernameOrderByCreatedAtDesc(String username);

    List<LeaveRequest> findByStatusOrderByCreatedAtDesc(String status);

    List<LeaveRequest> findAllByOrderByCreatedAtDesc();

    long countByUsernameAndStatus(String username, String status);
}
