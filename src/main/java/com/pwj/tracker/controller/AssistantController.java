package com.pwj.tracker.controller;

import com.pwj.tracker.dto.ApiResponse;
import com.pwj.tracker.dto.AssistantChatRequest;
import com.pwj.tracker.service.AssistantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantService assistantService;

    /**
     * POST /api/v1/assistant/chat
     * Body: { "messages": [{ "role": "user", "content": "..." }, ...], "userRole": "ENGINEER" }
     * In-app help assistant — answers questions about how to use the PWJ Tracker, returned as
     * an intro line + an ordered flow of steps so the frontend can render a step flow.
     */
    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<AssistantService.ChatAnswer>> chat(@RequestBody AssistantChatRequest req) {
        try {
            AssistantService.ChatAnswer answer = assistantService.chat(req);
            return ResponseEntity.ok(ApiResponse.ok("Reply", answer));
        } catch (Exception e) {
            log.error("Assistant chat failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
}
