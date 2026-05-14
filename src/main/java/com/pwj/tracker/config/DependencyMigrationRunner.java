package com.pwj.tracker.config;

import com.pwj.tracker.repository.PwjEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DependencyMigrationRunner implements ApplicationRunner {

    private final PwjEntryRepository repository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int updated = repository.backfillNullDependency();
        if (updated > 0) {
            log.info("Backfilled dependency='OH Approval' for {} entries", updated);
        }
    }
}
