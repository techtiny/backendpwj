package com.pwj.tracker.account.repository;

import com.pwj.tracker.account.entity.CrmContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CrmContactRepository extends JpaRepository<CrmContact, Long> {
    List<CrmContact> findByKindOrderByIdAsc(String kind);
}
