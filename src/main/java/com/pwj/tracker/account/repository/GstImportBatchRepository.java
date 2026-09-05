package com.pwj.tracker.account.repository;

import com.pwj.tracker.account.entity.GstImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface GstImportBatchRepository extends JpaRepository<GstImportBatch, Long> {
    List<GstImportBatch> findAllByOrderByUploadedAtDesc();
}
