package com.pwj.tracker.account.repository;

import com.pwj.tracker.account.entity.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {
    List<Campaign> findByOwnerRoleOrderByCreatedAtDesc(String ownerRole);
    List<Campaign> findByStatus(String status);
}
