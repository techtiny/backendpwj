package com.pwj.tracker.controller;

import com.pwj.tracker.dto.ApiResponse;
import com.pwj.tracker.dto.UserDto;
import com.pwj.tracker.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** GET /api/v1/users — Admin: all users */
    @GetMapping
    public ResponseEntity<ApiResponse<List<UserDto.UserResponse>>> getAllUsers() {
        return ResponseEntity.ok(ApiResponse.ok("Users fetched", userService.getAllUsers()));
    }

    /** GET /api/v1/users/engineers — Admin: engineers only */
    @GetMapping("/engineers")
    public ResponseEntity<ApiResponse<List<UserDto.UserResponse>>> getEngineers() {
        return ResponseEntity.ok(ApiResponse.ok("Engineers fetched", userService.getEngineers()));
    }

    /** POST /api/v1/users — Admin: create any user (engineer/procurement/admin) */
    @PostMapping
    public ResponseEntity<ApiResponse<UserDto.UserResponse>> createUser(
            @Valid @RequestBody UserDto.CreateUserRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("User created", userService.createUser(req)));
    }

    /** PATCH /api/v1/users/{id}/name — Admin: update user full name */
    @PatchMapping("/{id}/name")
    public ResponseEntity<ApiResponse<UserDto.UserResponse>> updateFullName(
            @PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok("Name updated", userService.updateFullName(id, body.get("fullName"))));
    }

    /** PATCH /api/v1/users/{id}/username — Admin: update username */
    @PatchMapping("/{id}/username")
    public ResponseEntity<ApiResponse<UserDto.UserResponse>> updateUsername(
            @PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok("Username updated", userService.updateUsername(id, body.get("username"))));
    }

    /** PATCH /api/v1/users/{id}/phone — Admin: update user phone */
    @PatchMapping("/{id}/phone")
    public ResponseEntity<ApiResponse<UserDto.UserResponse>> updatePhone(
            @PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok("Phone updated", userService.updatePhone(id, body.get("phone"))));
    }

    /** PATCH /api/v1/users/{id}/password — Admin: change user password */
    @PatchMapping("/{id}/password")
    public ResponseEntity<ApiResponse<UserDto.UserResponse>> changePassword(
            @PathVariable Long id, @Valid @RequestBody UserDto.ChangePasswordRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Password updated", userService.changePassword(id, req.getNewPassword())));
    }

    /** DELETE /api/v1/users/{id} — Admin: deactivate user */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivateUser(@PathVariable Long id) {
        userService.deactivateUser(id);
        return ResponseEntity.ok(ApiResponse.ok("User deactivated", null));
    }
}
