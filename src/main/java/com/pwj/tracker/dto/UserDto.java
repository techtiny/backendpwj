package com.pwj.tracker.dto;

import com.pwj.tracker.model.AppUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class UserDto {

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class LoginRequest {
        @NotBlank public String username;
        @NotBlank public String password;
        public boolean force; // true = kick out existing session
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class LoginResponse {
        public Long id;
        public String username;
        public String fullName;
        public AppUser.Role role;
        public String designation;
        public String token;
        public Boolean isTestAccount;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CreateUserRequest {
        @NotBlank public String username;
        @NotBlank public String password;
        @NotBlank public String fullName;
        public String email;
        public String phone;
        public String employeeNumber;   // optional — auto-assigned (EMP0001…) when blank
        @NotNull  public AppUser.Role role;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ChangePasswordRequest {
        @NotBlank(message = "New password is required")
        public String newPassword;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UserResponse {
        public Long id;
        public String username;
        public String fullName;
        public String email;
        public String phone;
        public String designation;
        public String employeeNumber;
        public AppUser.Role role;
        public Boolean active;
        public LocalDateTime createdAt;
        // Employee-exit details
        public Boolean exited;
        public LocalDate exitDate;
        public String exitType;
        public String exitReason;
        public String exitMarkedBy;
        public LocalDateTime exitMarkedAt;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class MarkExitRequest {
        public LocalDate exitDate;   // last working day
        public String exitType;      // RESIGNED | TERMINATED | ABSCONDED | RETIRED | OTHER
        public String exitReason;
        @NotBlank public String markedBy;
    }
}
