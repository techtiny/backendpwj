package com.pwj.tracker.repository;

import com.pwj.tracker.model.Resignation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ResignationRepository extends JpaRepository<Resignation, Long> {
    Optional<Resignation> findFirstByUsernameIgnoreCaseAndStatusInOrderByCreatedAtDesc(String username, Collection<String> statuses);
    Optional<Resignation> findFirstByUsernameIgnoreCaseOrderByCreatedAtDesc(String username);
    List<Resignation> findAllByOrderByCreatedAtDesc();
    List<Resignation> findByStatus(String status);
}
