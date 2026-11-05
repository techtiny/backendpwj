package com.pwj.tracker.account.repository;

import com.pwj.tracker.account.entity.PaymentRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRequestRepository extends JpaRepository<PaymentRequest, Long> {

    List<PaymentRequest> findAllByOrderByRequestedAtDesc();

    List<PaymentRequest> findByPwjEntryIdAndStatus(Long pwjEntryId, String status);
}
