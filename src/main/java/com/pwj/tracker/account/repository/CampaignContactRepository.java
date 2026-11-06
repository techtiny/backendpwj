package com.pwj.tracker.account.repository;

import com.pwj.tracker.account.entity.CampaignContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CampaignContactRepository extends JpaRepository<CampaignContact, Long> {
    List<CampaignContact> findByOwnerRoleOrderByNameAsc(String ownerRole);
    Optional<CampaignContact> findByOwnerRoleAndPhone(String ownerRole, String phone);
}
