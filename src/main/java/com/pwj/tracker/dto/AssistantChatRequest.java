package com.pwj.tracker.dto;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssistantChatRequest {

    private List<Turn> messages; // full conversation so far, oldest first
    private String userRole;     // e.g. "ENGINEER", "PROCUREMENT" — lets the assistant tailor its answer

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Turn {
        private String role;    // "user" or "assistant"
        private String content;
    }
}
