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
}
