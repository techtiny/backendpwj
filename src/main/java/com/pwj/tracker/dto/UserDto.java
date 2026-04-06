package com.pwj.tracker.dto;

import com.pwj.tracker.model.AppUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.time.LocalDateTime;

public class UserDto {

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class LoginRequest {
        @NotBlank public String username;
        @NotBlank public String password;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class LoginResponse {
        public Long id;
        public String username;
        public String fullName;
        public AppUser.Role role;
        public String token;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CreateUserRequest {
        @NotBlank public String username;
        @NotBlank public String password;
        @NotBlank public String fullName;
        public String email;
        @NotNull  public AppUser.Role role;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UserResponse {
        public Long id;
        public String username;
        public String fullName;
        public String email;
        public AppUser.Role role;
        public Boolean active;
        public LocalDateTime createdAt;
    }
}
