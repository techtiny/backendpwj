package com.pwj.tracker.repository;

import com.pwj.tracker.model.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface VendorRepository extends JpaRepository<Vendor, Long> {

    @Modifying
    @Transactional
    @Query(value = "UPDATE vendor SET active = true WHERE status = 'APPROVED' AND active = false", nativeQuery = true)
    int activateApprovedVendors();

    List<Vendor> findByActiveTrueOrderByNameAsc();

    List<Vendor> findByActiveTrueAndStatusOrderByCreatedAtDesc(Vendor.VendorStatus status);

    List<Vendor> findByActiveTrueAndStatusOrderByNameAsc(Vendor.VendorStatus status);

    List<Vendor> findAllByOrderByCreatedAtDesc();
    List<Vendor> findAllByOrderByUpdatedAtDesc();

    java.util.Optional<Vendor> findByNameAndActiveTrue(String name);
}
