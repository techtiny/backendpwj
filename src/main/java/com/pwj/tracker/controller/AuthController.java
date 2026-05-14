package com.pwj.tracker.controller;

import com.pwj.tracker.dto.ApiResponse;
import com.pwj.tracker.dto.UserDto;
import com.pwj.tracker.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    /** POST /api/v1/auth/login */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UserDto.LoginResponse>> login(
            @Valid @RequestBody UserDto.LoginRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Login successful", userService.login(req)));
    }

    /** POST /api/v1/auth/logout */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(value = "X-Session-Token", required = false) String token) {
        userService.logout(token);
        return ResponseEntity.ok(ApiResponse.ok("Logged out", null));
    }

    /** GET /api/v1/auth/validate */
    @GetMapping("/validate")
    public ResponseEntity<ApiResponse<Void>> validate(
            @RequestHeader(value = "X-Session-Token", required = false) String token) {
        if (userService.validateToken(token)) {
            return ResponseEntity.ok(ApiResponse.ok("Valid", null));
        }
        return ResponseEntity.status(401).body(ApiResponse.error("Session expired"));
    }
}
