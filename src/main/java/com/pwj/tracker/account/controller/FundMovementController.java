package com.pwj.tracker.account.controller;

import com.pwj.tracker.account.dto.FundMovementDto;
import com.pwj.tracker.account.service.FundMovementService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Happizo Fund Management — Inflow / Outflow movements. */
@RestController
@RequestMapping("/api/fund-management")
public class FundMovementController {

    private final FundMovementService service;

    public FundMovementController(FundMovementService service) {
        this.service = service;
    }

    /** GET /api/fund-management?direction=INFLOW|OUTFLOW (direction optional). */
    @GetMapping
    public ResponseEntity<List<FundMovementDto>> list(@RequestParam(required = false) String direction) {
        return ResponseEntity.ok(service.list(direction));
    }

    /** GET /api/fund-management/balances — per-project available fund (inflow − outflow). */
    @GetMapping("/balances")
    public ResponseEntity<List<Map<String, Object>>> balances() {
        return ResponseEntity.ok(service.balances());
    }

    /**
     * GET /api/fund-management/payment-funding — today's bank-transfer demand per project vs the
     * available fund, the resulting shortfalls, and the surplus projects that can cover them.
     */
    @GetMapping("/payment-funding")
    public ResponseEntity<Map<String, Object>> paymentFunding() {
        return ResponseEntity.ok(service.paymentFunding());
    }

    /** POST /api/fund-management/transfer — { fromProjectId, toProjectId, amount, remarks } */
    @PostMapping("/transfer")
    public ResponseEntity<Void> transfer(@RequestBody Map<String, Object> body) {
        Long from = body.get("fromProjectId") != null ? Long.valueOf(String.valueOf(body.get("fromProjectId"))) : null;
        Long to = body.get("toProjectId") != null ? Long.valueOf(String.valueOf(body.get("toProjectId"))) : null;
        java.math.BigDecimal amount = body.get("amount") != null
                ? new java.math.BigDecimal(String.valueOf(body.get("amount"))) : null;
        String remarks = body.get("remarks") != null ? String.valueOf(body.get("remarks")) : null;
        service.transferFund(from, to, amount, remarks);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<FundMovementDto> create(@RequestBody FundMovementDto dto) {
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
