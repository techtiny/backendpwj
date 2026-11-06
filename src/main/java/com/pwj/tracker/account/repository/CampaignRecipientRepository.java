package com.pwj.tracker.account.repository;

import com.pwj.tracker.account.entity.CampaignRecipient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CampaignRecipientRepository extends JpaRepository<CampaignRecipient, Long> {
    List<CampaignRecipient> findByCampaignIdOrderByIdAsc(Long campaignId);
    List<CampaignRecipient> findByCampaignIdAndStatusOrderByIdAsc(Long campaignId, String status);
    long countByCampaignIdAndStatus(Long campaignId, String status);
    void deleteByCampaignId(Long campaignId);
}
