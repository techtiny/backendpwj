package com.pwj.tracker.repository;

import com.pwj.tracker.model.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VendorRepository extends JpaRepository<Vendor, Long> {

    List<Vendor> findByActiveTrueOrderByNameAsc();

    List<Vendor> findByActiveTrueAndStatusOrderByCreatedAtDesc(Vendor.VendorStatus status);

    List<Vendor> findByActiveTrueAndStatusOrderByNameAsc(Vendor.VendorStatus status);

    List<Vendor> findAllByOrderByCreatedAtDesc();

    java.util.Optional<Vendor> findByNameAndActiveTrue(String name);
}
