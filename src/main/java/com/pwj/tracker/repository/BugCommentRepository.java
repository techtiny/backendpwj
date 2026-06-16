package com.pwj.tracker.repository;

import com.pwj.tracker.model.BugComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BugCommentRepository extends JpaRepository<BugComment, Long> {
    List<BugComment> findByBugIdOrderByCreatedAtAsc(Long bugId);
}
