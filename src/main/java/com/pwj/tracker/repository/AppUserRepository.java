package com.pwj.tracker.repository;

import com.pwj.tracker.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsernameAndActiveTrue(String username);

    Optional<AppUser> findBySessionToken(String sessionToken);

    List<AppUser> findAllByActiveTrue();

    List<AppUser> findByRoleAndActiveTrue(AppUser.Role role);

    boolean existsByUsername(String username);

    java.util.Optional<AppUser> findByFullNameAndActiveTrue(String fullName);
}
