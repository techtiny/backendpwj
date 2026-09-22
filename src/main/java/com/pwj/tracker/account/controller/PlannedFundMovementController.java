package com.pwj.tracker.account.controller;

import com.pwj.tracker.account.dto.PlannedFundMovementDto;
import com.pwj.tracker.account.service.PlannedFundMovementService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Happizo Fund Management — Planned Inflow / Planned Outflow (forecasted, not yet actual). */
@RestController
@RequestMapping("/api/fund-management/planned")
public class PlannedFundMovementController {

    private final PlannedFundMovementService service;

    public PlannedFundMovementController(PlannedFundMovementService service) {
        this.service = service;
    }

    /** GET /api/fund-management/planned?direction=INFLOW|OUTFLOW (direction optional). */
    @GetMapping
    public ResponseEntity<List<PlannedFundMovementDto>> list(@RequestParam(required = false) String direction) {
        return ResponseEntity.ok(service.list(direction));
    }

    @PostMapping
    public ResponseEntity<PlannedFundMovementDto> create(@RequestBody PlannedFundMovementDto dto) {
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
