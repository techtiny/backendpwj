package com.pwj.tracker.controller;

import com.pwj.tracker.dto.ApiResponse;
import com.pwj.tracker.model.OfficeExpense;
import com.pwj.tracker.model.OfficeStaff;
import com.pwj.tracker.service.OfficeExpenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** HR → Office/Mgnt expenses and office staff salaries; paid from Accounts → Weekly Payments → Office/Mgnt. */
@RestController
@RequestMapping("/api/v1/hr/office")
@RequiredArgsConstructor
public class OfficeExpenseController {

    private final OfficeExpenseService service;

    /** month = YYYY-MM or ALL; status = PENDING / PAID or blank. */
    @GetMapping("/expenses")
    public ResponseEntity<ApiResponse<List<OfficeExpense>>> list(@RequestParam String username,
                                                                @RequestParam(required = false) String month,
                                                                @RequestParam(required = false) String status,
                                                                @RequestParam(required = false) String division,
                                                                @RequestParam(required = false, defaultValue = "false") boolean forPayments) {
        return ResponseEntity.ok(ApiResponse.ok("Office expenses", service.list(username, month, status, division, forPayments)));
    }

    @PostMapping("/expenses")
    public ResponseEntity<ApiResponse<OfficeExpense>> raise(@RequestBody Map<String, Object> b) {
        return ResponseEntity.ok(ApiResponse.ok("Office expense raised", service.raise(str(b.get("username")), str(b.get("division")), str(b.get("frequency")), date(b.get("expenseDate")), date(b.get("cycleEndDate")),
                str(b.get("category")), str(b.get("description")), str(b.get("payee")), num(b.get("amount")), str(b.get("attachmentUrls")),
                str(b.get("bankName")), str(b.get("accountNumber")), str(b.get("ifsc")))));
    }

    @PutMapping("/expenses/{id}")
    public ResponseEntity<ApiResponse<OfficeExpense>> update(@PathVariable Long id, @RequestBody Map<String, Object> b) {
        return ResponseEntity.ok(ApiResponse.ok("Office expense updated", service.update(id, str(b.get("username")), date(b.get("expenseDate")), date(b.get("cycleEndDate")), b.containsKey("cycleEndDate"),
                str(b.get("category")), str(b.get("description")), str(b.get("payee")), num(b.get("amount")),
                str(b.get("remarks")), str(b.get("attachmentUrls")),
                str(b.get("bankName")), str(b.get("accountNumber")), str(b.get("ifsc")), str(b.get("frequency")))));
    }

    @DeleteMapping("/expenses/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, @RequestParam String username) {
        service.delete(id, username);
        return ResponseEntity.ok(ApiResponse.ok("Deleted", null));
    }

    @PutMapping("/expenses/{id}/paid")
    public ResponseEntity<ApiResponse<OfficeExpense>> paid(@PathVariable Long id, @RequestBody Map<String, Object> b) {
        return ResponseEntity.ok(ApiResponse.ok("Marked paid", service.markPaid(id, str(b.get("username")), date(b.get("paidDate")), str(b.get("paymentRef")))));
    }

    /** Send for Payment — body: username, ids (the lines put in the downloaded bank Excel). */
    @PostMapping("/expenses/send")
    public ResponseEntity<ApiResponse<List<OfficeExpense>>> send(@RequestBody Map<String, Object> b) {
        List<Long> ids = b.get("ids") instanceof List<?> l ? l.stream().map(x -> Long.valueOf(x.toString())).toList() : List.of();
        return ResponseEntity.ok(ApiResponse.ok("Sent for payment", service.sendForPayment(str(b.get("username")), ids)));
    }

    @PutMapping("/expenses/{id}/unpaid")
    public ResponseEntity<ApiResponse<OfficeExpense>> unpaid(@PathVariable Long id, @RequestBody Map<String, Object> b) {
        return ResponseEntity.ok(ApiResponse.ok("Marked unpaid", service.markUnpaid(id, str(b.get("username")))));
    }

    /** HR → Send for Payment — body: username, ids. */
    @PostMapping("/expenses/send-to-payments")
    public ResponseEntity<ApiResponse<List<OfficeExpense>>> sendToPayments(@RequestBody Map<String, Object> b) {
        return ResponseEntity.ok(ApiResponse.ok("Sent to Weekly Payments", service.sendToPayments(str(b.get("username")), ids(b))));
    }

    @PostMapping("/expenses/recall-from-payments")
    public ResponseEntity<ApiResponse<List<OfficeExpense>>> recall(@RequestBody Map<String, Object> b) {
        return ResponseEntity.ok(ApiResponse.ok("Taken back from Weekly Payments", service.recallFromPayments(str(b.get("username")), ids(b))));
    }

    /** Cash Flow — body: username, ids, on (true = add to Planned Outflow, false = remove). */
    @PostMapping("/expenses/cash-flow")
    public ResponseEntity<ApiResponse<List<OfficeExpense>>> cashFlowFlag(@RequestBody Map<String, Object> b) {
        boolean on = b.get("on") == null || Boolean.parseBoolean(b.get("on").toString());
        return ResponseEntity.ok(ApiResponse.ok(on ? "Added to Cash Flow" : "Removed from Cash Flow", service.setCashFlow(str(b.get("username")), ids(b), on)));
    }

    /** Planned outflow items for Funds → Cash Flow → Planned Outflow (marked lines + upcoming repeats). */
    @GetMapping("/cash-flow")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> cashFlow(@RequestParam String username) {
        return ResponseEntity.ok(ApiResponse.ok("Office/Mgnt planned outflow", service.cashFlow(username)));
    }

    private static List<Long> ids(Map<String, Object> b) {
        return b.get("ids") instanceof List<?> l ? l.stream().map(x -> Long.valueOf(x.toString())).toList() : List.of();
    }

    @GetMapping("/staff")
    public ResponseEntity<ApiResponse<List<OfficeStaff>>> staff(@RequestParam String username) {
        return ResponseEntity.ok(ApiResponse.ok("Office staff", service.staff(username)));
    }

    @PostMapping("/staff")
    public ResponseEntity<ApiResponse<OfficeStaff>> addStaff(@RequestBody Map<String, Object> b) {
        return ResponseEntity.ok(ApiResponse.ok("Staff added", service.addStaff(str(b.get("username")), str(b.get("name")),
                str(b.get("designation")), num(b.get("monthlyAmount")), str(b.get("startMonth")),
                str(b.get("bankName")), str(b.get("accountNumber")), str(b.get("ifsc")))));
    }

    @PutMapping("/staff/{id}")
    public ResponseEntity<ApiResponse<OfficeStaff>> updateStaff(@PathVariable Long id, @RequestBody Map<String, Object> b) {
        Object active = b.get("active");
        return ResponseEntity.ok(ApiResponse.ok("Staff updated", service.updateStaff(id, str(b.get("username")), str(b.get("name")),
                str(b.get("designation")), num(b.get("monthlyAmount")), active == null ? null : Boolean.valueOf(active.toString()),
                str(b.get("bankName")), str(b.get("accountNumber")), str(b.get("ifsc")))));
    }

    @DeleteMapping("/staff/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteStaff(@PathVariable Long id, @RequestParam String username) {
        service.deleteStaff(id, username);
        return ResponseEntity.ok(ApiResponse.ok("Staff deleted", null));
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }
    private static LocalDate date(Object o) { return o == null || o.toString().isBlank() ? null : LocalDate.parse(o.toString()); }
    private static BigDecimal num(Object o) { return o == null || o.toString().isBlank() ? null : new BigDecimal(o.toString()); }
}
