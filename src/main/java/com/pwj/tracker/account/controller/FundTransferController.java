package com.pwj.tracker.account.controller;

import com.pwj.tracker.account.dto.FundTransferDto;
import com.pwj.tracker.account.service.FundTransferService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/fund-transfers")
public class FundTransferController {

    private final FundTransferService service;

    public FundTransferController(FundTransferService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<FundTransferDto>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @GetMapping("/b2b-projects")
    public ResponseEntity<List<Map<String, Object>>> getProjects() {
        return ResponseEntity.ok(service.getAllProjects());
    }

    @GetMapping("/balance/{projectId}")
    public ResponseEntity<Map<String, Object>> getBalance(@PathVariable Long projectId) {
        BigDecimal balance = service.availableBalance(projectId);
        return ResponseEntity.ok(Map.of("projectId", projectId, "availableBalance", balance));
    }

    @PostMapping
    public ResponseEntity<FundTransferDto> create(@RequestBody FundTransferDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleValidation(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", ex.getMessage()));
    }
}
