package com.pwj.tracker.config;

import com.pwj.tracker.model.AppUser;
import com.pwj.tracker.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds one test login per role on startup. Entries created/edited under these
 * accounts are flagged isTestData=true and kept out of real users' views.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TestAccountSeeder implements ApplicationRunner {

    private final AppUserRepository userRepository;

    private static final String TEST_PASSWORD = "Test@123";

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed("test_admin",       "Test Admin",       AppUser.Role.ADMIN);
        seed("test_engineer",    "Test Engineer",    AppUser.Role.ENGINEER);
        seed("test_procurement", "Test Procurement", AppUser.Role.PROCUREMENT);
        seed("test_vp",          "Test VP",          AppUser.Role.VP);
        seed("test_oh",          "Test OH",          AppUser.Role.OH);
        seed("test_ceo",         "Test CEO",         AppUser.Role.CEO);
        seed("test_pm",          "Test PM",          AppUser.Role.PROJECT_MANAGER);
    }

    private void seed(String username, String fullName, AppUser.Role role) {
        if (userRepository.existsByUsername(username)) return;
        AppUser user = AppUser.builder()
                .username(username)
                .password(TEST_PASSWORD)
                .fullName(fullName)
                .role(role)
                .active(true)
                .isTestAccount(true)
                .build();
        userRepository.save(user);
        log.info("Seeded test account: {} (role={})", username, role);
    }
}
