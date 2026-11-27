package com.pwj.tracker.repository;

import com.pwj.tracker.model.OemContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OemContactRepository extends JpaRepository<OemContact, Long> {
    List<OemContact> findAllByOrderByIdAsc();
}
