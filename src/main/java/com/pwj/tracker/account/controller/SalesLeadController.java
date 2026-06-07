package com.pwj.tracker.account.controller;

import com.pwj.tracker.account.dto.SalesLeadDto;
import com.pwj.tracker.account.service.SalesLeadService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sales")
public class SalesLeadController {

    private final SalesLeadService service;

    public SalesLeadController(SalesLeadService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<SalesLeadDto>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SalesLeadDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        return ResponseEntity.ok(service.getSummary());
    }

    @PostMapping
    public ResponseEntity<SalesLeadDto> create(@RequestBody SalesLeadDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SalesLeadDto> update(@PathVariable Long id, @RequestBody SalesLeadDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
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
}
