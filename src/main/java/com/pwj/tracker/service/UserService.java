package com.pwj.tracker.service;

import com.pwj.tracker.dto.UserDto;
import com.pwj.tracker.dto.VendorRequest;
import com.pwj.tracker.model.AppUser;
import com.pwj.tracker.model.Vendor;
import com.pwj.tracker.repository.AppUserRepository;
import com.pwj.tracker.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final AppUserRepository userRepository;
    private final VendorRepository vendorRepository;
    private final VendorService vendorService;

    // ── Login (simple password check - no JWT for simplicity) ──
    @Transactional
    public UserDto.LoginResponse login(UserDto.LoginRequest req) {
        AppUser user = userRepository.findByUsernameAndActiveTrue(req.getUsername())
                .orElseThrow(() -> new RuntimeException("Invalid username or password"));

        if (!user.getPassword().equals(req.getPassword())) {
            throw new RuntimeException("Invalid username or password");
        }

        // Block second login if an active session exists (within last 8 hours)
        if (!req.isForce()
                && user.getSessionToken() != null
                && user.getSessionCreatedAt() != null
                && user.getSessionCreatedAt().isAfter(java.time.LocalDateTime.now().minusHours(8))) {
            throw new RuntimeException("ALREADY_LOGGED_IN");
        }

        String token = UUID.randomUUID().toString();
        user.setSessionToken(token);
        user.setSessionCreatedAt(java.time.LocalDateTime.now());
        userRepository.save(user);

        return UserDto.LoginResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .role(user.getRole())
                .designation(user.getDesignation())
                .token(token)
                .isTestAccount(Boolean.TRUE.equals(user.getIsTestAccount()))
                .build();
    }

    // ── Validate session token ──
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) return false;
        return userRepository.findBySessionToken(token).isPresent();
    }

    // ── Logout — clear session token ──
    @Transactional
    public void logout(String token) {
        userRepository.findBySessionToken(token).ifPresent(user -> {
            user.setSessionToken(null);
            user.setSessionCreatedAt(null);
            userRepository.save(user);
        });
    }

    // ── Create user (Admin only) ──
    @Transactional
    public UserDto.UserResponse createUser(UserDto.CreateUserRequest req) {
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new RuntimeException("Username '" + req.getUsername() + "' already exists");
        }
        AppUser user = AppUser.builder()
                .username(req.getUsername())
                .password(req.getPassword()) // hash in production
                .fullName(req.getFullName())
                .email(req.getEmail())
                .phone(req.getPhone())
                .role(req.getRole())
                .active(true)
                .build();
        return toResponse(userRepository.save(user));
    }

    // ── Get all users ──
    public List<UserDto.UserResponse> getAllUsers() {
        return userRepository.findAllByActiveTrue()
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ── Get engineers only ──
    public List<UserDto.UserResponse> getEngineers() {
        return userRepository.findByRoleAndActiveTrue(AppUser.Role.ENGINEER)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ── Update username ──
    @Transactional
    public UserDto.UserResponse updateUsername(Long id, String username) {
        if (username == null || username.isBlank()) throw new RuntimeException("Username cannot be empty");
        if (userRepository.existsByUsername(username.trim())) throw new RuntimeException("Username '" + username.trim() + "' already exists");
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setUsername(username.trim());
        return toResponse(userRepository.save(user));
    }

    // ── Update full name ──
    @Transactional
    public UserDto.UserResponse updateFullName(Long id, String fullName) {
        if (fullName == null || fullName.isBlank()) throw new RuntimeException("Full name cannot be empty");
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setFullName(fullName.trim());
        return toResponse(userRepository.save(user));
    }

    // ── Update phone ──
    @Transactional
    public UserDto.UserResponse updatePhone(Long id, String phone) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setPhone(phone);
        return toResponse(userRepository.save(user));
    }

    // ── Update designation ──
    @Transactional
    public UserDto.UserResponse updateDesignation(Long id, String designation) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setDesignation(designation == null || designation.isBlank() ? null : designation.trim());
        return toResponse(userRepository.save(user));
    }

    // ── Change password (Admin only) ──
    @Transactional
    public UserDto.UserResponse changePassword(Long id, String newPassword) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setPassword(newPassword);
        return toResponse(userRepository.save(user));
    }

    // ── Deactivate user ──
    @Transactional
    public void deactivateUser(Long id) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(false);
        userRepository.save(user);
    }

    // ── Vendor methods — delegated to VendorService ──
    public List<Vendor> getVendors() {
        return vendorService.getVendors();
    }

    @Transactional
    public Vendor createVendor(VendorRequest req) {
        return vendorService.createVendor(req);
    }

    private UserDto.UserResponse toResponse(AppUser u) {
        return UserDto.UserResponse.builder()
                .id(u.getId()).username(u.getUsername())
                .fullName(u.getFullName()).email(u.getEmail())
                .phone(u.getPhone()).designation(u.getDesignation())
                .role(u.getRole()).active(u.getActive())
                .createdAt(u.getCreatedAt()).build();
    }
}
