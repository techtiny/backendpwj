package com.pwj.tracker;

import com.pwj.tracker.repository.VendorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class PwjTrackerApplication {
    public static void main(String[] args) {
        SpringApplication.run(PwjTrackerApplication.class, args);
    }

    @Bean
    CommandLineRunner fixApprovedVendorsActive(VendorRepository vendorRepo) {
        return args -> {
            int fixed = vendorRepo.activateApprovedVendors();
            if (fixed > 0) log.info("Data fix: reactivated {} APPROVED vendor(s) that had active=false", fixed);
        };
    }
}
