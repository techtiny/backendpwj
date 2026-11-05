package com.pwj.tracker.account.controller;

import com.pwj.tracker.account.service.PaymentRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Payment requests on PO / WO documents — raised by the PR's engineer, approved by the allocated procurement person. */
@RestController
@RequestMapping("/api/payment-requests")
@RequiredArgsConstructor
public class PaymentRequestController {

    private final PaymentRequestService service;

    /** POST /api/payment-requests — body: username, pwjEntryId, amount, remarks, paymentMadeAgainst, paymentStage, docNumber. */
    @PostMapping
    public ResponseEntity<Map<String, Object>> raise(@RequestBody Map<String, Object> b) {
        return ResponseEntity.ok(service.raise(str(b.get("username")), Long.valueOf(String.valueOf(b.get("pwjEntryId"))),
                new BigDecimal(String.valueOf(b.get("amount"))), str(b.get("remarks")),
                str(b.get("paymentMadeAgainst")), str(b.get("paymentStage")), str(b.get("docNumber"))));
    }

    /** GET /api/payment-requests?username= — requests this user raised, must decide, or (oversight roles) all. */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(@RequestParam String username) {
        return ResponseEntity.ok(service.list(username));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable Long id, @RequestBody Map<String, Object> b) {
        return ResponseEntity.ok(service.approve(id, str(b.get("username"))));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Map<String, Object>> reject(@PathVariable Long id, @RequestBody Map<String, Object> b) {
        return ResponseEntity.ok(service.reject(id, str(b.get("username")), str(b.get("remark"))));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable Long id, @RequestBody Map<String, Object> b) {
        return ResponseEntity.ok(service.cancel(id, str(b.get("username"))));
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
}
